from __future__ import annotations

import json
import logging
import math
import os
import resource
import signal
import subprocess
import sys
import tempfile
import threading
import time
from dataclasses import asdict, dataclass
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import BinaryIO


HOST = "0.0.0.0"
PORT = 8081
EXECUTION_TIMEOUT_SECONDS = 8.0
MAX_CODE_BYTES = 64 * 1024
MAX_REQUEST_BYTES = MAX_CODE_BYTES + 4 * 1024
MAX_OUTPUT_BYTES = 128 * 1024
MAX_CONCURRENT_EXECUTIONS = 2

LOGGING_FORMAT = "%(asctime)s %(levelname)s %(message)s"
logging.basicConfig(level=logging.INFO, format=LOGGING_FORMAT)
LOGGER = logging.getLogger("python-runner")

EXECUTION_SLOTS = threading.BoundedSemaphore(MAX_CONCURRENT_EXECUTIONS)


@dataclass(frozen=True)
class ExecutionResult:
    output: str
    error: str
    exitCode: int | None
    timedOut: bool
    truncated: bool


def _apply_process_limits() -> None:
    os.umask(0o077)
    resource.setrlimit(
        resource.RLIMIT_CPU,
        (math.ceil(EXECUTION_TIMEOUT_SECONDS), math.ceil(EXECUTION_TIMEOUT_SECONDS) + 1),
    )
    resource.setrlimit(resource.RLIMIT_FSIZE, (16 * 1024 * 1024, 16 * 1024 * 1024))
    resource.setrlimit(resource.RLIMIT_NOFILE, (64, 64))
    resource.setrlimit(resource.RLIMIT_NPROC, (32, 32))
    resource.setrlimit(resource.RLIMIT_CORE, (0, 0))


def _terminate_process_group(process: subprocess.Popen[bytes]) -> None:
    if process.poll() is not None:
        return
    try:
        os.killpg(process.pid, signal.SIGKILL)
    except ProcessLookupError:
        pass


def execute_python(code: str) -> ExecutionResult:
    code_bytes = code.encode("utf-8")
    if not code.strip():
        return ExecutionResult("", "Código Python vazio", None, False, False)
    if len(code_bytes) > MAX_CODE_BYTES:
        return ExecutionResult("", "Código Python excede o limite permitido", None, False, False)

    with tempfile.TemporaryDirectory(prefix="execution__") as temporary_directory:
        workdir = Path(temporary_directory)
        script_path = workdir / "main.py"
        script_path.write_text(code, encoding="utf-8")

        environment = {
            "HOME": str(workdir),
            "PATH": "/usr/local/bin:/usr/bin:/bin",
            "LANG": "C.UTF-8",
            "LC_ALL": "C.UTF-8",
            "TZ": "UTC",
        }

        process = subprocess.Popen(
            [sys.executable, "-I", "-u", str(script_path)],
            cwd=workdir,
            env=environment,
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            start_new_session=True,
            preexec_fn=_apply_process_limits,
        )

        buffers: dict[str, bytearray] = {
            "stdout": bytearray(),
            "stderr": bytearray(),
        }
        output_lock = threading.Lock()
        output_limit_reached = threading.Event()

        def drain(stream: BinaryIO, destination: bytearray) -> None:
            while chunk := stream.read(4096):
                with output_lock:
                    current_size = len(buffers["stdout"]) + len(buffers["stderr"])
                    remaining = MAX_OUTPUT_BYTES - current_size
                    if remaining <= 0:
                        output_limit_reached.set()
                        return
                    destination.extend(chunk[:remaining])
                    if len(chunk) > remaining:
                        output_limit_reached.set()
                        return

        stdout_thread = threading.Thread(
            target=drain,
            args=(process.stdout, buffers["stdout"]),
            daemon=True,
        )
        stderr_thread = threading.Thread(
            target=drain,
            args=(process.stderr, buffers["stderr"]),
            daemon=True,
        )
        stdout_thread.start()
        stderr_thread.start()

        started_at = time.monotonic()
        timed_out = False
        truncated = False

        while process.poll() is None:
            if output_limit_reached.is_set():
                truncated = True
                _terminate_process_group(process)
                break
            if time.monotonic() - started_at >= EXECUTION_TIMEOUT_SECONDS:
                timed_out = True
                _terminate_process_group(process)
                break
            time.sleep(0.02)

        try:
            process.wait(timeout=1)
        except subprocess.TimeoutExpired:
            _terminate_process_group(process)
            process.wait(timeout=1)

        stdout_thread.join(timeout=1)
        stderr_thread.join(timeout=1)

        process.stdout.close()
        process.stderr.close()

        stdout = buffers["stdout"].decode("utf-8", errors="replace").strip()
        stderr = buffers["stderr"].decode("utf-8", errors="replace").strip()

        if timed_out:
            stderr = _append_error(stderr, "Execução interrompida por exceder o tempo limite")
        if truncated:
            stderr = _append_error(stderr, "Execução interrompida por exceder o limite de saída")

        return ExecutionResult(stdout, stderr, process.returncode, timed_out, truncated)


def _append_error(current_error: str, message: str) -> str:
    return f"{current_error}\n{message}".strip()


class RunnerRequestHandler(BaseHTTPRequestHandler):
    server_version = "SistemaMRPythonRunner/1.0"

    def do_GET(self) -> None:
        if self.path != "/health":
            self._write_json(HTTPStatus.NOT_FOUND, {"message": "Endpoint não encontrado"})
            return
        self._write_json(HTTPStatus.OK, {"status": "UP"})

    def do_POST(self) -> None:
        if self.path != "/execute":
            self._write_json(HTTPStatus.NOT_FOUND, {"message": "Endpoint não encontrado"})
            return

        request_length = self._request_length()
        if request_length is None:
            return

        try:
            payload = json.loads(self.rfile.read(request_length))
        except (UnicodeDecodeError, json.JSONDecodeError):
            self._write_json(HTTPStatus.BAD_REQUEST, {"message": "JSON inválido"})
            return

        code = payload.get("code") if isinstance(payload, dict) else None
        if not isinstance(code, str):
            self._write_json(HTTPStatus.BAD_REQUEST, {"message": "O campo code é obrigatório"})
            return

        if not EXECUTION_SLOTS.acquire(blocking=False):
            self._write_json(HTTPStatus.TOO_MANY_REQUESTS, {"message": "Runner ocupado"})
            return

        started_at = time.monotonic()
        try:
            result = execute_python(code)
            self._write_json(HTTPStatus.OK, asdict(result))
            LOGGER.info(
                "execution durationMs=%d exitCode=%s timedOut=%s truncated=%s outputBytes=%d errorBytes=%d",
                round((time.monotonic() - started_at) * 1000),
                result.exitCode,
                result.timedOut,
                result.truncated,
                len(result.output.encode("utf-8")),
                len(result.error.encode("utf-8")),
            )
        finally:
            EXECUTION_SLOTS.release()

    def _request_length(self) -> int | None:
        try:
            content_length = int(self.headers.get("Content-Length", ""))
        except ValueError:
            self._write_json(HTTPStatus.LENGTH_REQUIRED, {"message": "Content-Length inválido"})
            return None
        if content_length <= 0:
            self._write_json(HTTPStatus.LENGTH_REQUIRED, {"message": "Corpo da requisição ausente"})
            return None
        if content_length > MAX_REQUEST_BYTES:
            self._write_json(HTTPStatus.REQUEST_ENTITY_TOO_LARGE, {"message": "Requisição muito grande"})
            return None
        return content_length

    def _write_json(self, status: HTTPStatus, payload: dict[str, object]) -> None:
        response = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status.value)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(response)))
        self.end_headers()
        self.wfile.write(response)

    def log_message(self, message_format: str, *args: object) -> None:
        LOGGER.info("http " + message_format, *args)


def main() -> None:
    server = ThreadingHTTPServer((HOST, PORT), RunnerRequestHandler)
    server.daemon_threads = True
    LOGGER.info("Python runner iniciado em %s:%d", HOST, PORT)
    server.serve_forever()


if __name__ == "__main__":
    main()

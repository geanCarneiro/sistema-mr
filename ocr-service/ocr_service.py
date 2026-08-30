from __future__ import annotations

import base64
import binascii
import json
import logging
import os
import tempfile
import threading
import time
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any

from paddleocr import PaddleOCR


HOST = "0.0.0.0"
PORT = int(os.getenv("OCR_SERVICE_PORT", "8082"))
MODEL = os.getenv("PADDLEOCR_MODEL", "PP-OCRv6_medium")
ENGINE = os.getenv("PADDLEOCR_ENGINE", "onnxruntime")
MAX_FILE_BYTES = int(os.getenv("OCR_MAX_FILE_BYTES", str(20 * 1024 * 1024)))
MAX_REQUEST_BYTES = int(os.getenv("OCR_MAX_REQUEST_BYTES", str(32 * 1024 * 1024)))

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
LOGGER = logging.getLogger("ocr-service")
OCR_LOCK = threading.Lock()


def create_ocr() -> PaddleOCR:
    LOGGER.info("Carregando PaddleOCR model=%s engine=%s", MODEL, ENGINE)
    return PaddleOCR(
        text_detection_model_name=f"{MODEL}_det",
        text_recognition_model_name=f"{MODEL}_rec",
        use_doc_orientation_classify=True,
        use_doc_unwarping=False,
        use_textline_orientation=True,
        engine=ENGINE,
        device="cpu",
    )


OCR = create_ocr()


def _suffix_for(mime_type: str) -> str:
    return {
        "application/pdf": ".pdf",
        "image/png": ".png",
        "image/jpeg": ".jpg",
        "image/webp": ".webp",
        "image/tiff": ".tiff",
        "image/bmp": ".bmp",
    }.get(mime_type.lower(), ".bin")


def _result_data(result: Any) -> dict[str, Any]:
    data = result.json
    if callable(data):
        data = data()
    if not isinstance(data, dict):
        return {}
    nested = data.get("res")
    return nested if isinstance(nested, dict) else data


def _as_list(value: Any) -> list[Any]:
    if value is None:
        return []
    if hasattr(value, "tolist"):
        return value.tolist()
    return list(value)


def run_ocr(content: bytes, mime_type: str) -> dict[str, Any]:
    started_at = time.monotonic()
    lines: list[dict[str, Any]] = []
    suffix = _suffix_for(mime_type)

    with tempfile.TemporaryDirectory(prefix="paddleocr__") as temporary_directory:
        input_path = Path(temporary_directory) / f"input{suffix}"
        input_path.write_bytes(content)
        with OCR_LOCK:
            results = list(OCR.predict(str(input_path)))

    for result_index, result in enumerate(results):
        data = _result_data(result)
        texts = _as_list(data.get("rec_texts"))
        scores = _as_list(data.get("rec_scores"))
        raw_boxes = data.get("rec_polys")
        if raw_boxes is None:
            raw_boxes = data.get("rec_boxes")
        boxes = _as_list(raw_boxes)
        page = data.get("page_index")
        if not isinstance(page, int):
            page = result_index

        for index, text in enumerate(texts):
            normalized = str(text).strip()
            if not normalized:
                continue
            score = float(scores[index]) if index < len(scores) else 0.0
            box = boxes[index] if index < len(boxes) else []
            lines.append(
                {
                    "page": page,
                    "text": normalized,
                    "confidence": score,
                    "box": box,
                }
            )

    mean_confidence = (
        sum(line["confidence"] for line in lines) / len(lines) if lines else 0.0
    )
    return {
        "model": MODEL,
        "lines": lines,
        "meanConfidence": mean_confidence,
        "durationMs": round((time.monotonic() - started_at) * 1000),
    }


class OcrRequestHandler(BaseHTTPRequestHandler):
    server_version = "SistemaMROcrService/1.0"

    def do_GET(self) -> None:
        if self.path != "/health":
            self._write_json(HTTPStatus.NOT_FOUND, {"message": "Endpoint não encontrado"})
            return
        self._write_json(
            HTTPStatus.OK,
            {"status": "UP", "ready": True, "model": MODEL, "engine": ENGINE},
        )

    def do_POST(self) -> None:
        if self.path != "/ocr":
            self._write_json(HTTPStatus.NOT_FOUND, {"message": "Endpoint não encontrado"})
            return

        request_length = self._request_length()
        if request_length is None:
            return

        try:
            payload = json.loads(self.rfile.read(request_length))
            encoded = payload.get("contentBase64") if isinstance(payload, dict) else None
            mime_type = payload.get("mimeType") if isinstance(payload, dict) else None
            if not isinstance(encoded, str) or not encoded:
                raise ValueError("contentBase64 é obrigatório")
            if not isinstance(mime_type, str) or not mime_type:
                raise ValueError("mimeType é obrigatório")
            content = base64.b64decode(encoded, validate=True)
            if not content:
                raise ValueError("O arquivo está vazio")
            if len(content) > MAX_FILE_BYTES:
                self._write_json(
                    HTTPStatus.REQUEST_ENTITY_TOO_LARGE,
                    {"message": "O arquivo excede o limite permitido"},
                )
                return
        except (UnicodeDecodeError, json.JSONDecodeError, binascii.Error, ValueError) as exception:
            self._write_json(HTTPStatus.BAD_REQUEST, {"message": str(exception)})
            return

        try:
            result = run_ocr(content, mime_type)
            self._write_json(HTTPStatus.OK, result)
            LOGGER.info(
                "ocr mimeType=%s bytes=%d lines=%d confidence=%.4f durationMs=%d",
                mime_type,
                len(content),
                len(result["lines"]),
                result["meanConfidence"],
                result["durationMs"],
            )
        except Exception as exception:
            LOGGER.exception("Falha durante a inferência do PaddleOCR")
            self._write_json(
                HTTPStatus.UNPROCESSABLE_ENTITY,
                {"message": f"Falha na inferência OCR: {type(exception).__name__}"},
            )

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
            self._write_json(
                HTTPStatus.REQUEST_ENTITY_TOO_LARGE,
                {"message": "A requisição excede o limite permitido"},
            )
            return None
        return content_length

    def _write_json(self, status: HTTPStatus, payload: dict[str, Any]) -> None:
        response = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status.value)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(response)))
        self.end_headers()
        self.wfile.write(response)

    def log_message(self, message_format: str, *args: object) -> None:
        LOGGER.info("http " + message_format, *args)


def main() -> None:
    server = ThreadingHTTPServer((HOST, PORT), OcrRequestHandler)
    server.daemon_threads = True
    LOGGER.info("PaddleOCR service iniciado em %s:%d", HOST, PORT)
    server.serve_forever()


if __name__ == "__main__":
    main()

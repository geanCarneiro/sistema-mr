from __future__ import annotations

import base64
import json
import logging
import os
import threading
import time
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any


HOST = "0.0.0.0"
PORT = int(os.getenv("EMBEDDING_SERVICE_PORT", "8083"))
MODEL_NAME = os.getenv("EMBEDDING_MODEL", "intfloat/multilingual-e5-small")
MODEL_CACHE = os.getenv("EMBEDDING_MODEL_CACHE", "/models")
DEVICE = os.getenv("EMBEDDING_DEVICE", "cpu")
MAX_TEXTS = int(os.getenv("EMBEDDING_MAX_TEXTS", "20"))
MAX_TEXT_LENGTH = int(os.getenv("EMBEDDING_MAX_TEXT_LENGTH", "12000"))
MAX_REQUEST_BYTES = int(os.getenv("EMBEDDING_MAX_REQUEST_BYTES", str(2 * 1024 * 1024)))
MAX_BINARY_REQUEST_BYTES = int(os.getenv("LOCAL_AI_BINARY_MAX_REQUEST_BYTES", str(64 * 1024 * 1024)))
CHAT_MODEL_PATH = os.getenv("LOCAL_CHAT_MODEL_PATH", "/models/gemma-3-4b-it-q4_0.gguf")
CHAT_MODEL_NAME = os.getenv("LOCAL_CHAT_MODEL_NAME", "gemma-3-4b-it-q4")
CHAT_CONTEXT_SIZE = int(os.getenv("LOCAL_CHAT_CONTEXT_SIZE", "8192"))
CHAT_THREADS = int(os.getenv("LOCAL_CHAT_THREADS", "4"))
CHAT_FORMAT = os.getenv("LOCAL_CHAT_FORMAT", "gemma-3")
VISION_PROJECTOR_PATH = os.getenv("LOCAL_VISION_PROJECTOR_PATH", "")
MAX_CHAT_MESSAGES = int(os.getenv("LOCAL_CHAT_MAX_MESSAGES", "32"))
MAX_CHAT_TOKENS = int(os.getenv("LOCAL_CHAT_MAX_TOKENS", "2048"))

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
LOGGER = logging.getLogger("local-ai-service")
MODEL_LOCK = threading.Lock()


def create_model() -> Any:
    from sentence_transformers import SentenceTransformer

    LOGGER.info("Carregando embedding model=%s device=%s", MODEL_NAME, DEVICE)
    return SentenceTransformer(MODEL_NAME, cache_folder=MODEL_CACHE, device=DEVICE)


MODEL: Any | None = None
MODEL_LOAD_LOCK = threading.Lock()
CHAT_MODEL: Any | None = None
CHAT_MODEL_LOAD_LOCK = threading.Lock()


def get_model() -> Any:
    global MODEL
    if MODEL is None:
        with MODEL_LOAD_LOCK:
            if MODEL is None:
                MODEL = create_model()
    return MODEL


class LocalModelUnavailable(RuntimeError):
    pass


def create_chat_model() -> Any:
    if not os.path.isfile(CHAT_MODEL_PATH):
        raise LocalModelUnavailable(
            f"Modelo local não encontrado em {CHAT_MODEL_PATH}. "
            "Monte o GGUF da Gemma 3 4B IT no volume local_ai_models."
        )
    if VISION_PROJECTOR_PATH and not os.path.isfile(VISION_PROJECTOR_PATH):
        raise LocalModelUnavailable(
            f"Projetor de visão não encontrado em {VISION_PROJECTOR_PATH}. "
            "Monte o mmproj GGUF compatível no volume local_ai_models."
        )

    from llama_cpp import Llama

    LOGGER.info(
        "Carregando chat model=%s path=%s threads=%d context=%d",
        CHAT_MODEL_NAME,
        CHAT_MODEL_PATH,
        CHAT_THREADS,
        CHAT_CONTEXT_SIZE,
    )
    options: dict[str, Any] = {
        "model_path": CHAT_MODEL_PATH,
        "n_ctx": CHAT_CONTEXT_SIZE,
        "n_threads": CHAT_THREADS,
        "n_gpu_layers": 0,
        "verbose": False,
    }
    if VISION_PROJECTOR_PATH:
        from llama_cpp.llama_chat_format import Llava15ChatHandler

        class Gemma3ChatHandler(Llava15ChatHandler):
            DEFAULT_SYSTEM_MESSAGE = None
            CHAT_FORMAT = (
                "{% if messages[0]['role'] == 'system' %}"
                "{% if messages[0]['content'] is string %}"
                "<start_of_turn>user\n{{ messages[0]['content'] }}<end_of_turn>\n"
                "<start_of_turn>model\nUnderstood.<end_of_turn>\n"
                "{% endif %}{% endif %}"
                "{% for message in messages %}{% if message.role != 'system' %}"
                "<start_of_turn>{{ message.role }}\n"
                "{% if message.content is string %}{{ message.content }}"
                "{% else %}{% for content in message.content %}"
                "{% if content.type == 'text' and content.text %}{{ content.text }}{% endif %}"
                "{% if content.type == 'image_url' %}{{ content.image_url.url }}{% endif %}"
                "{% endfor %}{% endif %}"
                "<end_of_turn>\n"
                "{% endif %}{% endfor %}"
                "{% if add_generation_prompt %}<start_of_turn>model\n{% endif %}"
            )

        options["chat_handler"] = Gemma3ChatHandler(
            clip_model_path=VISION_PROJECTOR_PATH,
            verbose=False,
        )
    else:
        options["chat_format"] = CHAT_FORMAT
    return Llama(**options)


def get_chat_model() -> Any:
    global CHAT_MODEL
    if CHAT_MODEL is None:
        with CHAT_MODEL_LOAD_LOCK:
            if CHAT_MODEL is None:
                CHAT_MODEL = create_chat_model()
    return CHAT_MODEL


def validate_texts(value: Any) -> list[str]:
    if not isinstance(value, list) or not value:
        raise ValueError("texts deve ser uma lista não vazia")
    if len(value) > MAX_TEXTS:
        raise ValueError("A quantidade de textos excede o limite permitido")
    if any(not isinstance(text, str) or not text.strip() for text in value):
        raise ValueError("Cada texto deve ser uma string não vazia")
    if any(len(text) > MAX_TEXT_LENGTH for text in value):
        raise ValueError("Um texto excede o limite permitido")
    return value


def run_embedding(texts: list[str]) -> list[list[float]]:
    started_at = time.monotonic()
    with MODEL_LOCK:
        vectors = get_model().encode(
            texts,
            batch_size=min(len(texts), 8),
            normalize_embeddings=True,
            convert_to_numpy=True,
            show_progress_bar=False,
        )
    result = [vector.astype("float32").tolist() for vector in vectors]
    LOGGER.info("embed texts=%d dimensions=%d durationMs=%d", len(result), len(result[0]), round((time.monotonic() - started_at) * 1000))
    return result


def validate_messages(value: Any) -> list[dict[str, Any]]:
    if not isinstance(value, list) or not value:
        raise ValueError("messages deve ser uma lista não vazia")
    if len(value) > MAX_CHAT_MESSAGES:
        raise ValueError("A quantidade de mensagens excede o limite permitido")
    messages: list[dict[str, Any]] = []
    for message in value:
        if not isinstance(message, dict) or message.get("role") not in {"system", "user", "assistant", "tool"}:
            raise ValueError("Cada mensagem precisa possuir um role válido")
        content = message.get("content", "")
        if not isinstance(content, (str, list)):
            raise ValueError("O conteúdo da mensagem precisa ser texto ou conteúdo multimodal")
        messages.append(message)
    return messages


def run_chat(
    messages: list[dict[str, Any]],
    temperature: float,
    max_tokens: int,
    tools: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    started_at = time.monotonic()
    options: dict[str, Any] = {
        "messages": messages,
        "temperature": max(0.0, min(temperature, 1.5)),
        "max_tokens": max(1, min(max_tokens, MAX_CHAT_TOKENS)),
    }
    if tools:
        options["tools"] = tools
        options["tool_choice"] = "auto"
    with MODEL_LOCK:
        response = get_chat_model().create_chat_completion(**options)
    LOGGER.info(
        "chat messages=%d durationMs=%d",
        len(messages),
        round((time.monotonic() - started_at) * 1000),
    )
    return response


def parse_json_content(content: Any) -> dict[str, Any]:
    if not isinstance(content, str) or not content.strip():
        raise ValueError("O modelo local retornou conteúdo vazio")
    value = content.strip()
    if value.startswith("```"):
        first_line_break = value.find("\n")
        closing_fence = value.rfind("```")
        if first_line_break >= 0 and closing_fence > first_line_break:
            value = value[first_line_break + 1:closing_fence].strip()
    parsed = json.loads(value)
    if not isinstance(parsed, dict):
        raise ValueError("O modelo local não retornou um objeto JSON")
    return parsed


def first_message_content(response: dict[str, Any]) -> Any:
    choices = response.get("choices")
    if not isinstance(choices, list) or not choices or not isinstance(choices[0], dict):
        raise ValueError("O modelo local retornou uma resposta sem choices")
    message = choices[0].get("message")
    if not isinstance(message, dict):
        raise ValueError("O modelo local retornou uma mensagem inválida")
    return message


class EmbeddingRequestHandler(BaseHTTPRequestHandler):
    server_version = "SistemaMRLocalAiService/1.0"

    def do_GET(self) -> None:
        if self.path not in {"/health", "/health/chat", "/health/vision"}:
            self._write_json(HTTPStatus.NOT_FOUND, {"message": "Endpoint não encontrado"})
            return
        chat_model_present = os.path.isfile(CHAT_MODEL_PATH)
        vision_projector_present = bool(VISION_PROJECTOR_PATH) and os.path.isfile(VISION_PROJECTOR_PATH)
        payload = {
            "status": "UP",
            "ready": MODEL is not None,
            "model": MODEL_NAME,
            "device": DEVICE,
            "chatModel": CHAT_MODEL_NAME,
            "chatModelPath": CHAT_MODEL_PATH,
            "chatModelPresent": chat_model_present,
            "chatReady": CHAT_MODEL is not None and chat_model_present,
            "visionProjectorPath": VISION_PROJECTOR_PATH or None,
            "visionProjectorPresent": vision_projector_present,
            "visionReady": CHAT_MODEL is not None and chat_model_present and vision_projector_present,
        }
        if self.path == "/health/chat":
            payload["status"] = "READY" if payload["chatModelPresent"] else "MISSING_MODEL"
            self._write_json(HTTPStatus.OK if payload["chatModelPresent"] else HTTPStatus.SERVICE_UNAVAILABLE, payload)
            return
        if self.path == "/health/vision":
            payload["status"] = "READY" if payload["visionProjectorPresent"] else "MISSING_PROJECTOR"
            self._write_json(HTTPStatus.OK if payload["visionProjectorPresent"] else HTTPStatus.SERVICE_UNAVAILABLE, payload)
            return
        if not payload["ready"]:
            payload["status"] = "DEGRADED"
        self._write_json(HTTPStatus.OK, payload)

    def do_POST(self) -> None:
        if self.path not in {"/embed", "/chat", "/decision", "/vision"}:
            self._write_json(HTTPStatus.NOT_FOUND, {"message": "Endpoint não encontrado"})
            return
        content_type = self.headers.get("Content-Type", "").split(";", 1)[0].strip().lower()
        length = self._request_length(
            MAX_BINARY_REQUEST_BYTES
            if self.path == "/vision" and content_type != "application/json"
            else MAX_REQUEST_BYTES
        )
        if length is None:
            return
        try:
            if self.path == "/vision" and content_type != "application/json":
                mime_type = content_type
                prompt = self.headers.get("X-Prompt", "").strip()
                if not mime_type or not prompt:
                    raise ValueError("Content-Type e X-Prompt são obrigatórios")
                try:
                    max_tokens = int(self.headers.get("X-Max-Tokens", "2048"))
                except ValueError as exception:
                    raise ValueError("X-Max-Tokens deve ser numérico") from exception
                content_base64 = base64.b64encode(self.rfile.read(length)).decode("ascii")
                if not content_base64:
                    raise ValueError("O arquivo está vazio")
                image_message = {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": prompt},
                        {"type": "image_url", "image_url": {
                            "url": f"data:{mime_type};base64,{content_base64}"
                        }},
                    ],
                }
                raw = first_message_content(run_chat(
                    [{"role": "system", "content": "Descreva fielmente a imagem e transcreva seu texto."}, image_message],
                    0.1,
                    max_tokens,
                ))
                self._write_json(HTTPStatus.OK, {"model": CHAT_MODEL_NAME, "content": raw.get("content", "")})
                return

            payload = json.loads(self.rfile.read(length))
            if not isinstance(payload, dict):
                raise ValueError("O corpo deve ser um objeto JSON")
            if self.path == "/embed":
                texts = validate_texts(payload.get("texts"))
                embeddings = run_embedding(texts)
                self._write_json(HTTPStatus.OK, {"model": MODEL_NAME, "embeddings": embeddings})
            elif self.path == "/chat":
                messages = validate_messages(payload.get("messages"))
                response = run_chat(
                    messages,
                    float(payload.get("temperature", 0.2)),
                    int(payload.get("maxTokens", 2048)),
                    payload.get("tools") if isinstance(payload.get("tools"), list) else None,
                )
                self._write_json(HTTPStatus.OK, {"model": CHAT_MODEL_NAME, **response})
            elif self.path == "/decision":
                prompt = payload.get("prompt")
                if not isinstance(prompt, str) or not prompt.strip():
                    raise ValueError("prompt deve ser uma string não vazia")
                messages = [
                    {
                        "role": "system",
                        "content": (
                            "Interprete a intenção do usuário para uma decisão de privacidade. "
                            "Retorne somente JSON com intent, confidence e explanation. "
                            "Os intents permitidos são ALLOW_MINIMIZED, DENY, ALLOW_FULL, RETRY_ANALYSIS, KEEP_BLOCKED e CLARIFY."
                        ),
                    },
                    {"role": "user", "content": prompt},
                ]
                raw = first_message_content(run_chat(messages, 0.2, 256))
                decision = parse_json_content(raw.get("content"))
                self._write_json(HTTPStatus.OK, {"model": CHAT_MODEL_NAME, "decision": decision})
            else:
                content_base64 = payload.get("contentBase64")
                mime_type = payload.get("mimeType")
                prompt = payload.get("prompt")
                if not all(isinstance(value, str) and value.strip() for value in (content_base64, mime_type, prompt)):
                    raise ValueError("contentBase64, mimeType e prompt são obrigatórios")
                image_message = {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": prompt},
                        {
                            "type": "image_url",
                            "image_url": {"url": f"data:{mime_type};base64,{content_base64}"},
                        },
                    ],
                }
                raw = first_message_content(run_chat(
                    [{"role": "system", "content": "Descreva fielmente a imagem e transcreva seu texto."}, image_message],
                    0.1,
                    int(payload.get("maxTokens", 2048)),
                ))
                self._write_json(HTTPStatus.OK, {"model": CHAT_MODEL_NAME, "content": raw.get("content", "")})
        except (UnicodeDecodeError, json.JSONDecodeError, ValueError) as exception:
            self._write_json(HTTPStatus.BAD_REQUEST, {"message": str(exception)})
        except LocalModelUnavailable as exception:
            self._write_json(HTTPStatus.SERVICE_UNAVAILABLE, {"message": str(exception)})
        except Exception:
            LOGGER.exception("Falha durante a inferência de embeddings")
            self._write_json(HTTPStatus.UNPROCESSABLE_ENTITY, {"message": "Falha na inferência local"})

    def _request_length(self, limit: int = MAX_REQUEST_BYTES) -> int | None:
        try:
            length = int(self.headers.get("Content-Length", ""))
        except ValueError:
            self._write_json(HTTPStatus.LENGTH_REQUIRED, {"message": "Content-Length inválido"})
            return None
        if length <= 0:
            self._write_json(HTTPStatus.LENGTH_REQUIRED, {"message": "Corpo da requisição ausente"})
            return None
        if length > limit:
            self._write_json(HTTPStatus.REQUEST_ENTITY_TOO_LARGE, {"message": "A requisição excede o limite permitido"})
            return None
        return length

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
    get_model()
    server = ThreadingHTTPServer((HOST, PORT), EmbeddingRequestHandler)
    server.daemon_threads = True
    LOGGER.info("Local AI service iniciado em %s:%d", HOST, PORT)
    server.serve_forever()


if __name__ == "__main__":
    main()

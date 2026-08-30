FROM python:3.12.11-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PADDLE_PDX_MODEL_SOURCE=BOS \
    PADDLEOCR_MODEL=PP-OCRv6_medium \
    PADDLEOCR_ENGINE=onnxruntime \
    HOME=/opt/ocr

WORKDIR /app

RUN apt-get update \
    && apt-get install --no-install-recommends -y libgl1 libglib2.0-0 libgomp1 \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10002 ocr \
    && useradd --uid 10002 --gid ocr --home-dir /opt/ocr --create-home --shell /usr/sbin/nologin ocr

COPY requirements.txt ./
RUN python -m pip install --no-cache-dir --only-binary=:all: --requirement requirements.txt

COPY --chown=ocr:ocr ocr_service.py ./

RUN python -m py_compile /app/ocr_service.py

USER ocr
RUN python -c "import ocr_service; print('Modelos PaddleOCR carregados com sucesso')"

EXPOSE 8082
ENTRYPOINT ["python", "/app/ocr_service.py"]

FROM python:3.12.11-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    HOME=/opt/local-ai \
    EMBEDDING_MODEL_CACHE=/models \
    EMBEDDING_DEVICE=cpu \
    LOCAL_CHAT_MODEL_PATH=/models/gemma-3-4b-it-q4_0.gguf \
    LOCAL_CHAT_MODEL_NAME=gemma-3-4b-it-q4 \
    LOCAL_CHAT_CONTEXT_SIZE=8192 \
    LOCAL_CHAT_THREADS=4 \
    LOCAL_CHAT_FORMAT=gemma-3 \
    OMP_NUM_THREADS=2 \
    MKL_NUM_THREADS=2 \
    TOKENIZERS_PARALLELISM=false

WORKDIR /app

RUN groupadd --gid 10003 localai \
    && useradd --uid 10003 --gid localai --home-dir /opt/local-ai --create-home --shell /usr/sbin/nologin localai \
    && mkdir -p /models \
    && chown -R localai:localai /app /models /opt/local-ai

COPY requirements.txt ./
RUN python -m pip install --no-cache-dir --only-binary=:all: \
    --extra-index-url https://download.pytorch.org/whl/cpu \
    --extra-index-url https://abetlen.github.io/llama-cpp-python/whl/cpu \
    --requirement requirements.txt

COPY --chown=localai:localai embedding_service.py ./

RUN python -m py_compile /app/embedding_service.py

USER localai
EXPOSE 8083
ENTRYPOINT ["python", "/app/embedding_service.py"]

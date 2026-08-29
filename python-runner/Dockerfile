FROM python:3.12-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    TZ=UTC \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8

WORKDIR /app

RUN apt-get update \
    && apt-get install --no-install-recommends -y iptables util-linux \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 runner \
    && useradd --uid 10001 --gid runner --no-create-home --shell /usr/sbin/nologin runner

COPY requirements.txt ./
RUN pip install --no-cache-dir --requirement requirements.txt

COPY runner.py test_runner.py entrypoint.sh ./
RUN python -m unittest -v test_runner.py \
    && rm test_runner.py \
    && chmod 0555 /app/entrypoint.sh /app/runner.py

EXPOSE 8081

ENTRYPOINT ["/app/entrypoint.sh"]

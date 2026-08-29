#!/bin/sh
set -eu

# O serviço pode responder a conexões recebidas, mas nem ele nem o código
# executado podem iniciar conexões de saída. Loopback é usado pelo healthcheck.
iptables -P OUTPUT DROP
iptables -A OUTPUT -o lo -j ACCEPT
iptables -A OUTPUT -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT

exec setpriv \
    --reuid=runner \
    --regid=runner \
    --init-groups \
    --no-new-privs \
    python /app/runner.py

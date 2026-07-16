#!/usr/bin/env bash
# Guardrail: Caddy→web hop must be trusted for nginx real_ip / limit_req.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WEB_NGINX="$ROOT/../campus-trade-web/nginx.conf"
EDGE_COMPOSE="$ROOT/multi-node/docker-compose.edge.yml"
ENV_EXAMPLE="$ROOT/multi-node/.env.example"

fail() { echo "FAIL: $*" >&2; exit 1; }

[[ -f "$WEB_NGINX" ]] || fail "missing $WEB_NGINX"
grep -q 'set_real_ip_from 127.0.0.1;' "$WEB_NGINX" \
  || fail "nginx.conf must trust loopback for host Caddy"
grep -q 'set_real_ip_from 172.16.0.0/12;' "$WEB_NGINX" \
  || fail "nginx.conf must trust Docker bridge for published 127.0.0.1:8080"
grep -q 'set_real_ip_from ${TRUSTED_PROXY_CIDR};' "$WEB_NGINX" \
  || fail "nginx.conf must still honor TRUSTED_PROXY_CIDR"

grep -q 'GATEWAY_UPSTREAM: \${GATEWAY_UPSTREAM:?' "$EDGE_COMPOSE" \
  || fail "edge compose must require GATEWAY_UPSTREAM (gateway removed from edge)"
grep -q '"127.0.0.1:\${WEB_PORT:-8080}:8080"' "$EDGE_COMPOSE" \
  || fail "edge compose must hard-bind web to 127.0.0.1"

grep -q '^GATEWAY_UPSTREAM=' "$ENV_EXAMPLE" \
  || fail ".env.example must document GATEWAY_UPSTREAM"
grep -q '^TRUSTED_PROXY_CIDR=172.16.0.0/12$' "$ENV_EXAMPLE" \
  || fail ".env.example TRUSTED_PROXY_CIDR must be Docker bridge, not VPC-only"

echo "OK: edge real_ip / GATEWAY_UPSTREAM guards present"

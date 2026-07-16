#!/usr/bin/env bash
# 轻量集群探测：仅输出 OK/FAIL 与容器状态，不打印 Secret。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# 只读取非敏感地址变量（不 source 整个 .env，避免密码进入 shell 环境）
if [[ -f .env ]]; then
  while IFS= read -r line; do
    [[ "$line" =~ ^[[:space:]]*# ]] && continue
    [[ "$line" =~ ^[[:space:]]*$ ]] && continue
    case "$line" in
      HOST_IP=*|MW_HOST=*|NACOS_HOST=*|MYSQL_HOST=*|REDIS_HOST=*|NACOS_PORT=*|MYSQL_PORT=*|REDIS_PORT=*)
        # shellcheck disable=SC2163
        export "$line"
        ;;
    esac
  done < .env
fi

ok() { echo "  OK  $*"; }
fail() { echo "  FAIL $*"; FAIL=1; }
FAIL=0

check_http() {
  local name="$1" url="$2"
  if curl -fsS --max-time 5 "$url" >/dev/null 2>&1; then ok "$name $url"; else fail "$name $url"; fi
}

echo "== Docker containers (local)"
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' 2>/dev/null || fail "docker ps"

echo ""
echo "== Local data layer"
if docker ps --format '{{.Names}}' | grep -qx campus-mysql-primary; then
  if docker exec campus-mysql-primary sh -c 'mysqladmin ping -h localhost -uroot -p"$MYSQL_ROOT_PASSWORD" --silent' 2>/dev/null; then
    ok "mysql-primary ping"
    docker exec campus-mysql-primary sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SELECT CONCAT(\"gtid_mode=\", @@gtid_mode, \" read_only=\", @@read_only);"' 2>/dev/null | sed 's/^/  /' || true
  else
    fail "mysql-primary ping"
  fi
fi

if docker ps --format '{{.Names}}' | grep -qx campus-redis-master; then
  if docker exec campus-redis-master sh -c 'redis-cli -a "$REDIS_PASSWORD" --no-auth-warning ping' 2>/dev/null | grep -q PONG; then
    ok "redis-master ping"
  else
    fail "redis-master ping"
  fi
fi

if docker ps --format '{{.Names}}' | grep -qx campus-redis-sentinel-1; then
  if docker exec campus-redis-sentinel-1 redis-cli -p 26379 ping 2>/dev/null | grep -q PONG; then
    ok "redis-sentinel-1 ping"
  else
    fail "redis-sentinel-1 ping"
  fi
fi

echo ""
echo "== Remote (optional, from .env addresses only)"
NACOS_ADDR="${NACOS_HOST:-${MW_HOST:-}}"
if [[ -n "$NACOS_ADDR" ]]; then
  check_http "nacos" "http://${NACOS_ADDR}:${NACOS_PORT:-8848}/nacos/"
fi

if [[ -n "${MYSQL_HOST:-}" && "${MYSQL_HOST}" != "${HOST_IP:-}" ]]; then
  if docker run --rm --network host mysql:8.0 mysqladmin ping -h"${MYSQL_HOST}" --connect-timeout=3 --silent 2>/dev/null; then
    ok "remote mysql port ${MYSQL_HOST}:${MYSQL_PORT:-3306}"
  else
    fail "remote mysql port ${MYSQL_HOST}:${MYSQL_PORT:-3306}"
  fi
fi

echo ""
if [[ $FAIL -eq 0 ]]; then echo "ALL CHECKS PASSED"; else echo "SOME CHECKS FAILED"; exit 1; fi

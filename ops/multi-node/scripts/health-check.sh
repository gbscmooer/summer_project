#!/usr/bin/env bash
# 按角色严格检查本机预期容器集合；缺少任意容器立即失败。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

ROLE="${1:?usage: $0 edge|mw|app-a|app-b|app-c|data-primary|data-replica}"

if [[ -f .env ]]; then
  # shellcheck disable=SC1091
  set -a
  source .env
  set +a
fi

RUNNING="$(docker ps --format '{{.Names}}' 2>/dev/null || true)"
ok() { echo "  OK  $*"; }
fail() { echo "  FAIL $*"; FAIL=1; }
FAIL=0

has() { echo "$RUNNING" | grep -qx "$1"; }

require_container() {
  local name="$1"
  if has "$name"; then
    ok "container ${name} running"
  else
    fail "missing required container ${name}"
  fi
}

check_http() {
  local name="$1" url="$2"
  if curl -fsS --max-time 5 "$url" >/dev/null 2>&1; then ok "$name"; else fail "$name"; fi
}

check_tcp() {
  local name="$1" host="$2" port="$3"
  if nc -z -w 3 "$host" "$port" 2>/dev/null; then ok "$name"; else fail "$name"; fi
}

echo "== health-check role=${ROLE}"

case "$ROLE" in
  edge)
    EXPECTED=(campus-web campus-caddy)
    ;;
  mw)
    EXPECTED=(campus-nacos campus-es campus-rabbitmq)
    ;;
  app-a)
    EXPECTED=(campus-gateway campus-user campus-product)
    ;;
  app-b)
    EXPECTED=(campus-gateway campus-product campus-order)
    ;;
  app-c)
    EXPECTED=(campus-user campus-order)
    ;;
  data-primary)
    EXPECTED=(campus-mysql-primary campus-redis-master campus-redis-sentinel-1)
    ;;
  data-replica)
    EXPECTED=(campus-mysql-replica campus-redis-replica campus-redis-sentinel-2)
    ;;
  *)
    echo "ERROR: unknown role '${ROLE}'" >&2
    echo "usage: $0 edge|mw|app-a|app-b|app-c|data-primary|data-replica" >&2
    exit 1
    ;;
esac

for c in "${EXPECTED[@]}"; do
  require_container "$c"
done

if [[ $FAIL -ne 0 ]]; then
  echo ""
  echo "SOME CHECKS FAILED"
  exit 1
fi

case "$ROLE" in
  edge)
    if docker exec campus-web wget -qO- http://127.0.0.1:8080/ >/dev/null 2>&1; then
      ok "campus-web HTTP"
    else
      fail "campus-web HTTP"
    fi
    check_tcp "caddy :80" 127.0.0.1 80
    check_tcp "caddy :443" 127.0.0.1 443
    ;;
  mw)
    check_http "nacos" "http://127.0.0.1:${NACOS_PORT:-8848}/nacos/"
    check_http "elasticsearch" "http://127.0.0.1:${ES_PORT:-9200}/_cluster/health"
    if docker exec campus-rabbitmq rabbitmq-diagnostics -q ping >/dev/null 2>&1; then
      ok "rabbitmq health"
    else
      fail "rabbitmq health"
    fi
    ;;
  app-a)
    check_tcp "campus-gateway :8080" 127.0.0.1 8080
    check_tcp "campus-user :8081" 127.0.0.1 8081
    check_tcp "campus-product :8082" 127.0.0.1 8082
    ;;
  app-b)
    check_tcp "campus-gateway :8080" 127.0.0.1 8080
    check_tcp "campus-product :8082" 127.0.0.1 8082
    check_tcp "campus-order :8083" 127.0.0.1 8083
    ;;
  app-c)
    check_tcp "campus-user :8081" 127.0.0.1 8081
    check_tcp "campus-order :8083" 127.0.0.1 8083
    ;;
  data-primary)
    if docker exec campus-mysql-primary sh -c 'mysqladmin ping -h localhost -uroot -p"$MYSQL_ROOT_PASSWORD" --silent' 2>/dev/null; then
      ok "mysql-primary health"
    else
      fail "mysql-primary health"
    fi
    if docker exec campus-redis-master sh -c 'redis-cli -a "$REDIS_PASSWORD" --no-auth-warning ping' 2>/dev/null | grep -q PONG; then
      ok "redis-master PONG"
    else
      fail "redis-master PING"
    fi
    if docker exec campus-redis-sentinel-1 redis-cli -p 26379 ping 2>/dev/null | grep -q PONG; then
      ok "sentinel-1 PING"
    else
      fail "sentinel-1 PING"
    fi
    ;;
  data-replica)
    if docker exec campus-mysql-replica sh -c 'mysqladmin ping -h localhost -uroot -p"$MYSQL_ROOT_PASSWORD" --silent' 2>/dev/null; then
      ok "mysql-replica health"
      status="$(docker exec campus-mysql-replica sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SHOW REPLICA STATUS\\G"' 2>/dev/null || true)"
      io="$(echo "$status" | awk -F': ' '/Replica_IO_Running:/{gsub(/ /,"",$2); print $2}')"
      sql="$(echo "$status" | awk -F': ' '/Replica_SQL_Running:/{gsub(/ /,"",$2); print $2}')"
      io_err="$(echo "$status" | awk -F': ' '/Last_IO_Error:/{sub(/^[^:]+: /,""); print}')"
      sql_err="$(echo "$status" | awk -F': ' '/Last_SQL_Error:/{sub(/^[^:]+: /,""); print}')"
      [[ "$io" == "Yes" ]] && ok "Replica_IO_Running=Yes" || fail "Replica_IO_Running=${io:-<empty>}"
      [[ "$sql" == "Yes" ]] && ok "Replica_SQL_Running=Yes" || fail "Replica_SQL_Running=${sql:-<empty>}"
      [[ -z "${io_err}" ]] && ok "Last_IO_Error empty" || fail "Last_IO_Error present"
      [[ -z "${sql_err}" ]] && ok "Last_SQL_Error empty" || fail "Last_SQL_Error present"
    else
      fail "mysql-replica health"
    fi
    info="$(docker exec campus-redis-replica sh -c 'redis-cli -a "$REDIS_PASSWORD" --no-auth-warning INFO replication' 2>/dev/null || true)"
    link="$(echo "$info" | awk -F: '/^master_link_status:/{gsub(/\r/,"",$2); print $2}')"
    [[ "$link" == "up" ]] && ok "redis-replica master_link_status=up" || fail "redis-replica master_link_status=${link:-<empty>}"
    master="$(docker exec campus-redis-sentinel-2 redis-cli -p 26379 SENTINEL get-master-addr-by-name campus-redis 2>/dev/null || true)"
    if echo "$master" | grep -q .; then
      ok "sentinel-2 campus-redis master"
    else
      fail "sentinel-2 campus-redis master"
    fi
    ;;
esac

echo ""
if [[ $FAIL -eq 0 ]]; then
  echo "ALL CHECKS PASSED"
else
  echo "SOME CHECKS FAILED"
  exit 1
fi

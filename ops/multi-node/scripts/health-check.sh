#!/usr/bin/env bash
# 轻量集群探测：按本机存在的角色容器检查，不打印 Secret。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

RUNNING="$(docker ps --format '{{.Names}}' 2>/dev/null || true)"
ok() { echo "  OK  $*"; }
fail() { echo "  FAIL $*"; FAIL=1; }
FAIL=0
ROLE_FOUND=0

has() { echo "$RUNNING" | grep -qx "$1"; }

check_http() {
  local name="$1" url="$2"
  if curl -fsS --max-time 5 "$url" >/dev/null 2>&1; then ok "$name"; else fail "$name"; fi
}

check_tcp() {
  local name="$1" host="$2" port="$3"
  if nc -z -w 3 "$host" "$port" 2>/dev/null; then ok "$name"; else fail "$name"; fi
}

echo "== Docker containers (local)"
if [[ -z "$RUNNING" ]]; then
  fail "no running containers"
else
  docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' 2>/dev/null || fail "docker ps"
fi

# --- Data-Main ---
if has campus-mysql-primary || has campus-redis-master || has campus-redis-sentinel-1; then
  ROLE_FOUND=1
  echo ""
  echo "== Data-Main"
  if has campus-mysql-primary; then
    if docker exec campus-mysql-primary sh -c 'mysqladmin ping -h localhost -uroot -p"$MYSQL_ROOT_PASSWORD" --silent' 2>/dev/null; then
      ok "mysql-primary health"
    else
      fail "mysql-primary health"
    fi
  fi
  if has campus-redis-master; then
    if docker exec campus-redis-master sh -c 'redis-cli -a "$REDIS_PASSWORD" --no-auth-warning ping' 2>/dev/null | grep -q PONG; then
      ok "redis-master PING"
    else
      fail "redis-master PING"
    fi
  fi
  if has campus-redis-sentinel-1; then
    if docker exec campus-redis-sentinel-1 redis-cli -p 26379 ping 2>/dev/null | grep -q PONG; then
      ok "sentinel-1 PING"
    else
      fail "sentinel-1 PING"
    fi
  fi
fi

# --- Data-Sub ---
if has campus-mysql-replica || has campus-redis-replica || has campus-redis-sentinel-2; then
  ROLE_FOUND=1
  echo ""
  echo "== Data-Sub"
  if has campus-mysql-replica; then
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
  fi
  if has campus-redis-replica; then
    info="$(docker exec campus-redis-replica sh -c 'redis-cli -a "$REDIS_PASSWORD" --no-auth-warning INFO replication' 2>/dev/null || true)"
    role="$(echo "$info" | awk -F: '/^role:/{gsub(/\r/,"",$2); print $2}')"
    link="$(echo "$info" | awk -F: '/^master_link_status:/{gsub(/\r/,"",$2); print $2}')"
    [[ "$role" == "slave" ]] && ok "redis-replica role=slave" || fail "redis-replica role=${role:-<empty>}"
    [[ "$link" == "up" ]] && ok "redis-replica master_link_status=up" || fail "redis-replica master_link_status=${link:-<empty>}"
  fi
  if has campus-redis-sentinel-2; then
    master="$(docker exec campus-redis-sentinel-2 redis-cli -p 26379 SENTINEL get-master-addr-by-name campus-redis 2>/dev/null || true)"
    if echo "$master" | grep -q .; then
      ok "sentinel-2 campus-redis master"
    else
      fail "sentinel-2 campus-redis master"
    fi
  fi
fi

# --- Middleware ---
if has campus-nacos || has campus-es || has campus-rabbitmq; then
  ROLE_FOUND=1
  echo ""
  echo "== Middleware"
  has campus-nacos && check_http "nacos" "http://127.0.0.1:${NACOS_PORT:-8848}/nacos/"
  has campus-es && check_http "elasticsearch" "http://127.0.0.1:${ES_PORT:-9200}/_cluster/health"
  if has campus-rabbitmq; then
    if docker exec campus-rabbitmq rabbitmq-diagnostics -q ping >/dev/null 2>&1; then
      ok "rabbitmq health"
    else
      fail "rabbitmq health"
    fi
  fi
fi

# --- App ---
if has campus-gateway || has campus-user || has campus-product || has campus-order; then
  ROLE_FOUND=1
  echo ""
  echo "== App"
  has campus-gateway && check_tcp "campus-gateway :8080" 127.0.0.1 8080
  has campus-user && check_tcp "campus-user :8081" 127.0.0.1 8081
  has campus-product && check_tcp "campus-product :8082" 127.0.0.1 8082
  has campus-order && check_tcp "campus-order :8083" 127.0.0.1 8083
fi

# --- Edge ---
if has campus-web || has campus-caddy; then
  ROLE_FOUND=1
  echo ""
  echo "== Edge"
  has campus-web && check_tcp "campus-web :8080" 127.0.0.1 8080
  has campus-caddy && check_tcp "caddy :80" 127.0.0.1 80
  has campus-caddy && check_tcp "caddy :443" 127.0.0.1 443
fi

echo ""
if [[ "$ROLE_FOUND" -eq 0 ]]; then
  fail "no cluster role containers detected on this host"
fi

if [[ $FAIL -eq 0 ]]; then
  echo "ALL CHECKS PASSED"
else
  echo "SOME CHECKS FAILED"
  exit 1
fi

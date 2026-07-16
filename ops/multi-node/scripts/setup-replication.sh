#!/usr/bin/env bash
# 在 Data-Sub 执行：配置本机 MySQL 为 GTID 从库（可安全重跑）。
# 不修改 Data-Main；仅用复制账号连主库。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

if [[ -f .env ]]; then
  # shellcheck disable=SC1091
  set -a
  source .env
  set +a
fi

: "${MYSQL_PRIMARY_HOST:?MYSQL_PRIMARY_HOST is required}"
: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"
: "${MYSQL_REPLICATION_PASSWORD:?MYSQL_REPLICATION_PASSWORD is required}"

MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_REPLICATION_USER="${MYSQL_REPLICATION_USER:-repl}"
REPLICA_CONTAINER="campus-mysql-replica"

mysql_exec() {
  docker exec -i "$REPLICA_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "$1"
}

mysql_exec_multi() {
  docker exec -i "$REPLICA_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$@"
}

read_replica_field() {
  local field="$1"
  mysql_exec "SHOW REPLICA STATUS\\G" 2>/dev/null \
    | awk -F': ' -v f="$field" '$1 ~ f {gsub(/^[ \t]+/, "", $2); print $2; exit}'
}

replica_channel_exists() {
  local source_host
  source_host="$(read_replica_field 'Source_Host' || true)"
  [[ -n "${source_host}" ]]
}

wait_replication_healthy() {
  local label="${1:-replication}"
  echo "==> waiting for ${label} IO/SQL=Yes..."
  for i in $(seq 1 60); do
    io_running="$(read_replica_field 'Replica_IO_Running')"
    sql_running="$(read_replica_field 'Replica_SQL_Running')"
    if [[ "$io_running" == "Yes" && "$sql_running" == "Yes" ]]; then
      echo "==> replication healthy"
      "$ROOT_DIR/scripts/enable-mysql-readonly.sh"
      mysql_exec_multi -e "SHOW REPLICA STATUS\\G" 2>/dev/null \
        | grep -E 'Replica_IO_Running:|Replica_SQL_Running:|Seconds_Behind_Source:|Source_Host:' || true
      return 0
    fi
    sleep 2
  done
  echo "ERROR: ${label} failed to reach IO=Yes SQL=Yes" >&2
  mysql_exec_multi -e "SHOW REPLICA STATUS\\G" 2>/dev/null \
    | grep -E 'Replica_IO_Running:|Replica_SQL_Running:|Last_IO_Error:|Last_SQL_Error:' || true
  return 1
}

echo "==> target container: ${REPLICA_CONTAINER}"
if ! docker ps --format '{{.Names}}' | grep -qx "$REPLICA_CONTAINER"; then
  echo "ERROR: container ${REPLICA_CONTAINER} is not running" >&2
  exit 1
fi

echo "==> waiting for local mysql-replica..."
for i in $(seq 1 60); do
  if docker exec "$REPLICA_CONTAINER" mysqladmin ping -h localhost -uroot -p"$MYSQL_ROOT_PASSWORD" --silent 2>/dev/null; then
    break
  fi
  sleep 2
  if [[ "$i" -eq 60 ]]; then
    echo "ERROR: local mysql-replica not ready" >&2
    exit 1
  fi
done

server_id="$(mysql_exec "SELECT @@server_id;")"
if [[ "$server_id" != "2" ]]; then
  echo "ERROR: expected @@server_id=2 on ${REPLICA_CONTAINER}, got ${server_id}" >&2
  exit 1
fi
echo "==> server_id=${server_id} OK"

table_count="$(mysql_exec "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='campus_trade';" 2>/dev/null || echo 0)"

if replica_channel_exists; then
  io_running="$(read_replica_field 'Replica_IO_Running')"
  sql_running="$(read_replica_field 'Replica_SQL_Running')"

  if [[ "$io_running" == "Yes" && "$sql_running" == "Yes" ]]; then
    echo "==> replication channel exists and IO/SQL=Yes; ensuring read-only"
    "$ROOT_DIR/scripts/enable-mysql-readonly.sh"
    mysql_exec_multi -e "SHOW REPLICA STATUS\\G" 2>/dev/null \
      | grep -E 'Replica_IO_Running:|Replica_SQL_Running:|Seconds_Behind_Source:|Source_Host:' || true
    exit 0
  fi

  echo "==> replication channel exists but IO/SQL not running; attempting START REPLICA"
  mysql_exec_multi -e "START REPLICA;" 2>/dev/null || true
  wait_replication_healthy "existing replication channel recovery" || exit 1
  exit 0
fi

if [[ "${table_count:-0}" -gt 0 ]]; then
  echo "ERROR: campus_trade has ${table_count} table(s) but no replication channel." >&2
  echo "       Refusing RESET. Back up if needed, then recreate an empty Data-Sub data directory" >&2
  echo "       (${DATA_MYSQL_DIR:-/data/mysql}) and run up-role.sh data-replica again." >&2
  exit 1
fi

echo "==> empty replica (no channel, campus_trade tables=0): initializing replication channel"

echo "==> probing primary via ${MYSQL_REPLICATION_USER}@${MYSQL_PRIMARY_HOST}:${MYSQL_PORT}..."
for i in $(seq 1 60); do
  if docker run --rm --network host mysql:8.0 \
    mysql -h "$MYSQL_PRIMARY_HOST" -P "$MYSQL_PORT" \
    -u"$MYSQL_REPLICATION_USER" -p"$MYSQL_REPLICATION_PASSWORD" \
    -e "SELECT 1" --connect-timeout=3 >/dev/null 2>&1; then
    break
  fi
  sleep 2
  if [[ "$i" -eq 60 ]]; then
    echo "ERROR: cannot connect to primary as ${MYSQL_REPLICATION_USER}" >&2
    exit 1
  fi
done

echo "==> RESET REPLICA / RESET MASTER / CHANGE REPLICATION SOURCE..."
mysql_exec_multi <<EOSQL
SET GLOBAL super_read_only=OFF;
SET GLOBAL read_only=OFF;
STOP REPLICA;
RESET REPLICA ALL;
RESET MASTER;
CHANGE REPLICATION SOURCE TO
  SOURCE_HOST='${MYSQL_PRIMARY_HOST}',
  SOURCE_PORT=${MYSQL_PORT},
  SOURCE_USER='${MYSQL_REPLICATION_USER}',
  SOURCE_PASSWORD='${MYSQL_REPLICATION_PASSWORD}',
  SOURCE_AUTO_POSITION=1,
  GET_SOURCE_PUBLIC_KEY=1;
START REPLICA;
EOSQL

wait_replication_healthy "new replication channel" || exit 1

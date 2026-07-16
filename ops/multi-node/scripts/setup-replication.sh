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

io_running="$(mysql_exec "SHOW REPLICA STATUS\\G" 2>/dev/null | awk -F': ' '/Replica_IO_Running:/{gsub(/ /,"",$2); print $2}' || true)"
sql_running="$(mysql_exec "SHOW REPLICA STATUS\\G" 2>/dev/null | awk -F': ' '/Replica_SQL_Running:/{gsub(/ /,"",$2); print $2}' || true)"

if [[ "$io_running" == "Yes" && "$sql_running" == "Yes" ]]; then
  echo "==> replication already running (IO=Yes SQL=Yes); skip RESET"
  mysql_exec_multi -e "SHOW REPLICA STATUS\\G" 2>/dev/null \
    | grep -E 'Replica_IO_Running:|Replica_SQL_Running:|Seconds_Behind_Source:|Source_Host:' || true
  exit 0
fi

table_count="$(mysql_exec "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='campus_trade';" 2>/dev/null || echo 0)"
if [[ "${table_count:-0}" -gt 0 ]]; then
  if [[ "${FORCE_REPLICA_RESET:-0}" != "1" ]]; then
    echo "ERROR: campus_trade has ${table_count} table(s) but replication is not healthy." >&2
    echo "       Refusing RESET. Set FORCE_REPLICA_RESET=1 to override." >&2
    mysql_exec_multi -e "SHOW REPLICA STATUS\\G" 2>/dev/null \
      | grep -E 'Replica_IO_Running:|Replica_SQL_Running:|Last_IO_Error:|Last_SQL_Error:' || true
    exit 1
  fi
  echo "==> FORCE_REPLICA_RESET=1: resetting existing replica data channel"
else
  echo "==> empty replica (campus_trade tables=0): initializing replication channel"
fi

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

echo "==> waiting for replication IO/SQL=Yes..."
for i in $(seq 1 60); do
  io_running="$(mysql_exec "SHOW REPLICA STATUS\\G" 2>/dev/null | awk -F': ' '/Replica_IO_Running:/{gsub(/ /,"",$2); print $2}' || true)"
  sql_running="$(mysql_exec "SHOW REPLICA STATUS\\G" 2>/dev/null | awk -F': ' '/Replica_SQL_Running:/{gsub(/ /,"",$2); print $2}' || true)"
  if [[ "$io_running" == "Yes" && "$sql_running" == "Yes" ]]; then
    echo "==> replication healthy"
    "$ROOT_DIR/scripts/enable-mysql-readonly.sh"
    mysql_exec_multi -e "SHOW REPLICA STATUS\\G" 2>/dev/null \
      | grep -E 'Replica_IO_Running:|Replica_SQL_Running:|Seconds_Behind_Source:|Source_Host:' || true
    exit 0
  fi
  sleep 2
done

echo "ERROR: replication failed to reach IO=Yes SQL=Yes" >&2
mysql_exec_multi -e "SHOW REPLICA STATUS\\G" 2>/dev/null \
  | grep -E 'Replica_IO_Running:|Replica_SQL_Running:|Last_IO_Error:|Last_SQL_Error:' || true
exit 1

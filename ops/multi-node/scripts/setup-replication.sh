#!/usr/bin/env bash
# 在 Data-Sub 执行：配置本机 MySQL 为 GTID 从库。
# 不修改 Data-Main；仅用 repl 账号连主库。
# 幂等：已在复制中则只确保只读；已有 channel 但未跑则 START REPLICA（不做 RESET）。
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
REPLICA_CONTAINER="${REPLICA_CONTAINER:-campus-mysql-replica}"

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

replica_status() {
  docker exec "$REPLICA_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SHOW REPLICA STATUS\G" 2>/dev/null || true
}

STATUS="$(replica_status)"
IO_RUNNING="$(printf '%s\n' "$STATUS" | awk '/Replica_IO_Running:/{print $2; exit}')"
SQL_RUNNING="$(printf '%s\n' "$STATUS" | awk '/Replica_SQL_Running:/{print $2; exit}')"
SOURCE_HOST="$(printf '%s\n' "$STATUS" | awk '/Source_Host:/{print $2; exit}')"

if [[ "${IO_RUNNING:-}" == "Yes" && "${SQL_RUNNING:-}" == "Yes" ]]; then
  echo "==> replica already running against ${SOURCE_HOST:-unknown}; ensuring read_only only"
  "$ROOT_DIR/scripts/enable-mysql-readonly.sh"
  docker exec "$REPLICA_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SHOW REPLICA STATUS\G" 2>/dev/null \
    | grep -E 'Replica_IO_Running:|Replica_SQL_Running:|Seconds_Behind_Source:|Last_IO_Error:|Last_SQL_Error:|Source_Host:'
  exit 0
fi

if [[ -n "${SOURCE_HOST:-}" ]]; then
  echo "==> replica channel exists (source=${SOURCE_HOST}) but not running; START REPLICA (no RESET)"
  docker exec -i "$REPLICA_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" <<'EOSQL'
SET GLOBAL super_read_only=OFF;
SET GLOBAL read_only=OFF;
START REPLICA;
EOSQL
  "$ROOT_DIR/scripts/enable-mysql-readonly.sh"
  docker exec "$REPLICA_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SHOW REPLICA STATUS\G" 2>/dev/null \
    | grep -E 'Replica_IO_Running:|Replica_SQL_Running:|Seconds_Behind_Source:|Last_IO_Error:|Last_SQL_Error:|Source_Host:'
  exit 0
fi

echo "==> probing primary via repl@${MYSQL_PRIMARY_HOST}:${MYSQL_PORT}..."
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

echo "==> configure GTID replica channel (first-time setup; no changes on primary)..."
# 临时关闭 super_read_only 以便 RESET / CHANGE SOURCE
# MySQL 8.0.46 用 RESET MASTER（RESET BINARY LOGS AND GTIDS 在部分镜像入口/客户端组合下会报语法错）
docker exec -i "$REPLICA_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" <<EOSQL
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

echo "==> restore read_only..."
"$ROOT_DIR/scripts/enable-mysql-readonly.sh"

echo "==> replica status summary"
docker exec "$REPLICA_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SHOW REPLICA STATUS\G" 2>/dev/null \
  | grep -E 'Replica_IO_Running:|Replica_SQL_Running:|Seconds_Behind_Source:|Last_IO_Error:|Last_SQL_Error:|Source_Host:'

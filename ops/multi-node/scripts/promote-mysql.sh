#!/usr/bin/env bash
# 答辩演示：将本机 MySQL 从库提升为主（人工 RPO/RTO）。
# 在 Data-Sub（原从）上执行，然后改各 App .env 的 MYSQL_HOST 并滚动重启。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

if [[ -f .env ]]; then
  # shellcheck disable=SC1091
  set -a
  source .env
  set +a
fi

: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"
CONTAINER="${REPLICA_CONTAINER:-campus-mysql-replica}"

if [[ "${OLD_PRIMARY_FENCED:-}" != "yes" ]]; then
  echo "ERROR: OLD_PRIMARY_FENCED=yes is required before any promote write." >&2
  echo "       Planned switch: run ./scripts/fence-mysql-primary.sh on Data-Main (SET PERSIST, not SET GLOBAL)." >&2
  echo "       Failure switch: confirm old primary is powered off, network cut, or SG isolated." >&2
  exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
  echo "ERROR: container ${CONTAINER} is not running" >&2
  exit 1
fi

read_replica_field() {
  local field="$1"
  docker exec "$CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SHOW REPLICA STATUS\\G" 2>/dev/null \
    | awk -F': ' -v f="$field" '$1 ~ f {gsub(/^[ \t]+/, "", $2); print $2; exit}'
}

io_running="$(read_replica_field 'Replica_IO_Running')"
sql_running="$(read_replica_field 'Replica_SQL_Running')"
last_io_err="$(read_replica_field 'Last_IO_Error')"
last_sql_err="$(read_replica_field 'Last_SQL_Error')"
lag="$(read_replica_field 'Seconds_Behind_Source')"

failure_promote=0

if [[ "$sql_running" != "Yes" ]]; then
  echo "ERROR: Replica_SQL_Running=${sql_running:-<empty>} (must be Yes)" >&2
  exit 1
fi

if [[ -n "${last_sql_err}" ]]; then
  echo "ERROR: Last_SQL_Error=${last_sql_err} (must be empty, even with FORCE_PROMOTE=1)" >&2
  exit 1
fi

if [[ "$io_running" != "Yes" ]]; then
  if [[ "${FORCE_PROMOTE:-0}" == "1" ]]; then
    failure_promote=1
    echo "WARN: failure switch: Replica_IO_Running=${io_running:-<empty>}" >&2
    echo "WARN: RPO data loss is possible when promoting with IO=No." >&2
  else
    echo "ERROR: Replica_IO_Running=${io_running:-<empty>} (expected Yes for planned switch)" >&2
    echo "       For failure switch with IO=No, set FORCE_PROMOTE=1 and OLD_PRIMARY_FENCED=yes." >&2
    exit 1
  fi
fi

if [[ "$failure_promote" -eq 0 ]]; then
  if [[ -n "${last_io_err}" ]]; then
    echo "ERROR: Last_IO_Error=${last_io_err} (planned switch requires no replication errors)" >&2
    exit 1
  fi
  if [[ "${lag:-}" != "0" ]]; then
    echo "ERROR: Seconds_Behind_Source=${lag:-<empty>} (planned switch requires lag = 0)" >&2
    exit 1
  fi
  echo "==> planned switch: IO/SQL=Yes, Seconds_Behind_Source=0, no replication errors"
else
  echo "==> failure switch: old primary fenced; promoting with possible RPO data loss"
fi

echo ""
echo "==> pre-promote confirmation (before opening new primary for writes)"
if [[ "$failure_promote" -eq 1 ]]; then
  echo "    OLD_PRIMARY_FENCED=yes: old primary confirmed powered off / network cut / SG isolated."
  echo "    WARNING: RPO data loss is possible for transactions not yet received by this replica."
else
  echo "    OLD_PRIMARY_FENCED=yes: old primary confirmed persistently read-only (SET PERSIST) and still online."
  echo "    Will wait until this replica has executed the old primary's @@GLOBAL.gtid_executed"
  echo "    (Seconds_Behind_Source=0 alone is not enough: IO may still be fetching)."
fi
echo ""

drain_timeout="${PROMOTE_DRAIN_TIMEOUT_SEC:-600}"
if ! [[ "$drain_timeout" =~ ^[0-9]+$ ]]; then
  echo "ERROR: PROMOTE_DRAIN_TIMEOUT_SEC must be an integer, got: ${drain_timeout}" >&2
  exit 1
fi

# Planned switch: Seconds_Behind_Source=0 only means SQL caught up to *already received*
# relay events. IO can still be behind the source binlog after fencing. Fetch the old
# primary's gtid_executed and WAIT_FOR_EXECUTED_GTID_SET so promote cannot drop
# committed-but-not-yet-fetched transactions.
if [[ "$failure_promote" -eq 0 ]]; then
  : "${MYSQL_PRIMARY_HOST:?MYSQL_PRIMARY_HOST is required for planned promote (old primary still online)}"
  MYSQL_PORT="${MYSQL_PORT:-3306}"

  echo "==> reading @@GLOBAL.gtid_executed from old primary ${MYSQL_PRIMARY_HOST}:${MYSQL_PORT}..."
  primary_gtids="$(docker run --rm --network host mysql:8.0 \
    mysql -h "$MYSQL_PRIMARY_HOST" -P "$MYSQL_PORT" \
    -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SELECT @@GLOBAL.gtid_executed" \
    --connect-timeout=5 2>/dev/null || true)"
  primary_gtids="$(echo "${primary_gtids:-}" | tr -d '\r' | tr -d '\n' | tr -d ' ')"
  if [[ -z "${primary_gtids}" ]]; then
    echo "ERROR: could not read @@GLOBAL.gtid_executed from ${MYSQL_PRIMARY_HOST}." >&2
    echo "       Planned switch requires the fenced old primary to be reachable from Data-Sub." >&2
    echo "       For failure switch (old primary unreachable), use FORCE_PROMOTE=1." >&2
    exit 1
  fi
  if ! [[ "$primary_gtids" =~ ^[0-9A-Fa-f:,-]+$ ]]; then
    echo "ERROR: unexpected gtid_executed from primary: ${primary_gtids}" >&2
    exit 1
  fi

  echo "==> waiting up to ${drain_timeout}s for replica to execute primary gtid_executed..."
  wait_rc="$(docker exec -i "$CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N <<EOSQL
SELECT WAIT_FOR_EXECUTED_GTID_SET('${primary_gtids}', ${drain_timeout});
EOSQL
)"
  wait_rc="$(echo "$wait_rc" | tr -d '\r' | awk 'NF{print; exit}')"
  if [[ "${wait_rc}" != "0" ]]; then
    echo "ERROR: WAIT_FOR_EXECUTED_GTID_SET(primary gtid_executed) returned ${wait_rc:-<empty>} (want 0)." >&2
    echo "       Refusing planned promote: replica has not fully caught up to the fenced primary." >&2
    exit 1
  fi
  echo "==> replica has executed old primary gtid_executed"
fi

# Always drain already-received relay events before RESET REPLICA ALL.
# FORCE_PROMOTE allows IO=No (transactions never received → unavoidable RPO), but must
# NOT discard transactions that arrived in the relay log and are still being applied.
echo "==> waiting up to ${drain_timeout}s for SQL thread to apply all received GTIDs..."
wait_rc="$(docker exec -i "$CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N <<EOSQL
SET @recv = (SELECT RECEIVED_TRANSACTION_SET FROM performance_schema.replication_connection_status WHERE CHANNEL_NAME='' LIMIT 1);
SELECT WAIT_FOR_EXECUTED_GTID_SET(IFNULL(@recv, ''), ${drain_timeout});
EOSQL
)"
wait_rc="$(echo "$wait_rc" | tr -d '\r' | awk 'NF{print; exit}')"
if [[ "${wait_rc}" != "0" ]]; then
  echo "ERROR: WAIT_FOR_EXECUTED_GTID_SET returned ${wait_rc:-<empty>} (want 0)." >&2
  echo "       Refusing promote: RESET REPLICA ALL would discard unapplied relay events." >&2
  exit 1
fi
echo "==> all received transactions applied (or none pending)"

echo "==> promoting ${CONTAINER} to writable primary..."
docker exec -i "$CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" <<'EOSQL'
STOP REPLICA;
RESET REPLICA ALL;
SET PERSIST super_read_only=OFF;
SET PERSIST read_only=OFF;
SELECT @@hostname AS host, @@read_only AS read_only, @@super_read_only AS super_read_only, @@server_id AS server_id;
EOSQL

cat <<'EOF'

==> PROMOTED. Manual steps required:
  1) 保持旧主只读（计划切换）或保持旧主隔离（故障切换）
  2) 更新所有 App 节点 .env: MYSQL_HOST=<新主私网 IP>
  3) 滚动重启 App-A / App-B / App-C（Gateway/User/Product/Order）
  4) 视情况将旧主改造成新从库（需人工规划 GTID）

EOF

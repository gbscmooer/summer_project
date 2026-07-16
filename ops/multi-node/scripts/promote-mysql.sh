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

if [[ "${OLD_PRIMARY_ISOLATED:-}" != "yes" ]]; then
  echo "ERROR: OLD_PRIMARY_ISOLATED=yes is required before any promote write." >&2
  echo "       Stop campus-mysql-primary or cut network to the old primary first." >&2
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
  echo "ERROR: Last_SQL_Error=${last_sql_err} (must be empty)" >&2
  exit 1
fi

if [[ "$io_running" != "Yes" ]]; then
  if [[ "${FORCE_PROMOTE:-0}" == "1" ]]; then
    failure_promote=1
    echo "WARN: failure switch: Replica_IO_Running=${io_running:-<empty>}" >&2
    echo "WARN: RPO data loss is possible when promoting with IO=No." >&2
  else
    echo "ERROR: Replica_IO_Running=${io_running:-<empty>} (expected Yes for planned switch)" >&2
    echo "       For failure switch with IO=No, set FORCE_PROMOTE=1 and OLD_PRIMARY_ISOLATED=yes." >&2
    exit 1
  fi
fi

if [[ "$failure_promote" -eq 0 ]]; then
  if [[ -n "${last_io_err}" ]]; then
    echo "ERROR: Last_IO_Error=${last_io_err} (planned switch requires no replication errors)" >&2
    exit 1
  fi
  if [[ -n "${lag}" && "${lag}" != "NULL" ]] && [[ "${lag}" -gt 5 ]]; then
    echo "ERROR: Seconds_Behind_Source=${lag} (planned switch requires lag <= 5)" >&2
    exit 1
  fi
  echo "==> planned switch: IO/SQL=Yes, no replication errors, lag <= 5s"
else
  echo "==> failure switch: old primary already isolated; promoting with possible RPO data loss"
fi

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
  1) 确认旧主已隔离（OLD_PRIMARY_ISOLATED=yes 已满足）
  2) 更新所有 App 节点 .env: MYSQL_HOST=<新主私网 IP>
  3) 滚动重启 App-A / App-B / App-C（Gateway/User/Product/Order）
  4) 视情况将旧主改造成新从库（需人工规划 GTID）

EOF

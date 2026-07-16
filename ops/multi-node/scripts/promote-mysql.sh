#!/usr/bin/env bash
# 答辩演示：将本机 MySQL 从库提升为主（人工 RPO/RTO）。
# 在新主（原从）上执行，然后改各 App .env 的 MYSQL_HOST 并滚动重启。
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

echo "==> promoting ${CONTAINER} to writable primary..."
docker exec -i "$CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" <<'EOSQL'
STOP REPLICA;
RESET REPLICA ALL;
SET GLOBAL super_read_only=OFF;
SET GLOBAL read_only=OFF;
SELECT @@hostname AS host, @@read_only AS read_only, @@super_read_only AS super_read_only, @@server_id AS server_id;
EOSQL

echo "==> promoted. Next:"
echo "  1) 更新各应用 .env: MYSQL_HOST=<本机私网IP>"
echo "  2) 滚动重启 App 节点"
echo "  3) 视情况把旧主改造成新从"

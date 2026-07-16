#!/usr/bin/env bash
# 计划切换：在 Data-Main 上将旧主持久化为只读，再去 Data-Sub 执行 promote-mysql.sh。
# 必须用 SET PERSIST（不是 SET GLOBAL）：否则 mysqld 容器重启后旧主会重新可写，
# 在 App 尚未全部切到新主的窗口内形成双主分叉。
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
CONTAINER="${PRIMARY_CONTAINER:-campus-mysql-primary}"

if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
  echo "ERROR: container ${CONTAINER} is not running" >&2
  exit 1
fi

echo "==> fencing ${CONTAINER} with persistent super_read_only..."
docker exec -i "$CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" <<'EOSQL'
SET PERSIST read_only=ON;
SET PERSIST super_read_only=ON;
SELECT @@hostname AS host,
       @@read_only AS read_only,
       @@super_read_only AS super_read_only,
       @@server_id AS server_id;
EOSQL

echo "==> fenced. Keep this node read-only until Apps point at the new primary."
echo "    Then on Data-Sub: OLD_PRIMARY_FENCED=yes ./scripts/promote-mysql.sh"
echo "    To abort before promote: ./scripts/unfence-mysql-primary.sh"

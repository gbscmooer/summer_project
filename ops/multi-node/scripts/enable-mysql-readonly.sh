#!/usr/bin/env bash
# 从库就绪后打开只读（首次 init 不能带 super_read_only）。
# 写入 datadir 标记，供 compose entrypoint 在容器重启后继续强制只读。
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"
if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi
: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"
CONTAINER="${REPLICA_CONTAINER:-campus-mysql-replica}"
READONLY_MARKER="${READONLY_MARKER:-/var/lib/mysql/.campus-super-read-only}"

docker exec -i "$CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" <<'EOSQL'
SET GLOBAL read_only=ON;
SET GLOBAL super_read_only=ON;
SELECT @@server_id AS server_id, @@read_only AS read_only, @@super_read_only AS super_read_only;
EOSQL

docker exec "$CONTAINER" sh -c "touch '${READONLY_MARKER}'"
echo "==> read_only enabled; marker ${READONLY_MARKER} written for restart persistence"

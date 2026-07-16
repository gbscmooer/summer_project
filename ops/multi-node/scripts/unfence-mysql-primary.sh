#!/usr/bin/env bash
# 撤销计划切换围栏（仅在尚未 promote / 确认无双写风险时使用）。
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

echo "==> removing persistent read-only fence on ${CONTAINER}..."
docker exec -i "$CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" <<'EOSQL'
SET PERSIST super_read_only=OFF;
SET PERSIST read_only=OFF;
SELECT @@hostname AS host,
       @@read_only AS read_only,
       @@super_read_only AS super_read_only,
       @@server_id AS server_id;
EOSQL

echo "==> unfenced (writable)."

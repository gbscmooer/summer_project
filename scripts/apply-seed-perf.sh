#!/usr/bin/env bash
# Apply perf seed data and rebuild Elasticsearch index.
# Usage: ./scripts/apply-seed-perf.sh [BASE_URL]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BASE_URL="${1:-http://127.0.0.1:8080}"

if [[ ! -f "$ROOT/.env" ]]; then
  echo "Missing .env in project root" >&2
  exit 1
fi

# shellcheck disable=SC1091
source "$ROOT/.env"

echo "==> Importing sql/seed-perf.sql ..."
docker exec -i campus-mysql mysql --default-character-set=utf8mb4 -ucampus -p"${MYSQL_PASSWORD}" campus_trade < "$ROOT/sql/seed-perf.sql"

echo "==> Verifying row counts ..."
docker exec campus-mysql mysql --default-character-set=utf8mb4 -ucampus -p"${MYSQL_PASSWORD}" campus_trade -N -e \
  "SELECT CONCAT('users=', COUNT(*)) FROM t_user; SELECT CONCAT('products=', COUNT(*)) FROM t_product;"

echo "==> Flushing stale Redis product cache ..."
docker exec campus-redis redis-cli -a "${REDIS_PASSWORD}" --no-auth-warning FLUSHDB >/dev/null

echo "==> Logging in as admin ..."
LOGIN_JSON=$(curl -sf -X POST "${BASE_URL}/api/user/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}')

TOKEN=$(python3 -c "import json,sys; d=json.load(sys.stdin); assert d.get('code')==200, d; print(d['data']['token'])" <<<"$LOGIN_JSON")
ROLE=$(python3 -c "import json,sys; d=json.load(sys.stdin); print(d['data']['role'])" <<<"$LOGIN_JSON")
echo "    admin role=$ROLE"

echo "==> Reindexing Elasticsearch ..."
REINDEX_JSON=$(curl -sf -X POST "${BASE_URL}/api/product/reindex" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json")

COUNT=$(python3 -c "import json,sys; d=json.load(sys.stdin); assert d.get('code')==200, d; print(d['data'])" <<<"$REINDEX_JSON")
echo "    indexed products: $COUNT"

echo "==> Smoke test search (keyword=数学) ..."
curl -sf --get "${BASE_URL}/api/product/search" \
  --data-urlencode "keyword=数学" \
  --data-urlencode "pageSize=5" | python3 -c \
  "import json,sys; d=json.load(sys.stdin); t=d['data']['total']; print(f'    search hits total={t}'); assert t>0"

echo "Done. Accounts: admin/123456 (管理员), zhangsan/123456, perfuser001..perfuser1500/123456"

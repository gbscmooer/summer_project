#!/usr/bin/env bash
# 验证商品 saleType 购买分流：普通走 /api/order，秒杀走 /api/order/seckill，并校验接口互斥。
# Usage: ./scripts/test-sale-type-routing.sh [BASE_URL]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BASE_URL="${1:-http://127.0.0.1:8080}"

if [[ ! -f "$ROOT/.env" ]]; then
  echo "Missing .env in project root" >&2
  exit 1
fi
# shellcheck disable=SC1091
source "$ROOT/.env"

pass=0
fail=0

ok() { echo "  PASS: $*"; pass=$((pass + 1)); }
bad() { echo "  FAIL: $*"; fail=$((fail + 1)); }

json_code() {
  python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('code'))"
}

json_field() {
  local path="$1"
  python3 -c "import json,sys; d=json.load(sys.stdin); print($path)"
}

echo "==> Flush Redis product/seckill cache (avoid stale detail without saleType)"
docker exec campus-redis redis-cli -a "${REDIS_PASSWORD}" --no-auth-warning FLUSHDB >/dev/null

echo "==> Login as perfuser001 (buyer, not seller of #1)"
LOGIN_JSON=$(curl -sf -X POST "${BASE_URL}/api/user/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"perfuser001","password":"123456"}')
TOKEN=$(python3 -c "import json,sys; d=json.load(sys.stdin); assert d.get('code')==200, d; print(d['data']['token'])" <<<"$LOGIN_JSON")
AUTH="Authorization: Bearer ${TOKEN}"

echo "==> Pick products: seckill(#1) vs normal"
SECKILL_ID=1
NORMAL_JSON=$(curl -sf "${BASE_URL}/api/product/list?pageNum=1&pageSize=20")
NORMAL_ID=$(python3 -c "
import json,sys
d=json.load(sys.stdin)
rows=d.get('data',{}).get('records') or d.get('data',{}).get('list') or []
for r in rows:
    pid=r.get('productId') or r.get('id')
    st=r.get('saleType', 0)
    if pid and int(pid)!=${SECKILL_ID} and int(st or 0)==0 and int(r.get('status') or 0)==1:
        print(pid); break
else:
    raise SystemExit('no normal on-sale product found')
" <<<"$NORMAL_JSON")

# 若列表未带 saleType，用详情确认
DETAIL_S=$(curl -sf "${BASE_URL}/api/product/${SECKILL_ID}")
DETAIL_N=$(curl -sf "${BASE_URL}/api/product/${NORMAL_ID}")
S_TYPE=$(python3 -c "import json,sys; d=json.load(sys.stdin); print(d['data'].get('saleType'))" <<<"$DETAIL_S")
N_TYPE=$(python3 -c "import json,sys; d=json.load(sys.stdin); print(d['data'].get('saleType'))" <<<"$DETAIL_N")
echo "    seckill productId=${SECKILL_ID} saleType=${S_TYPE}"
echo "    normal  productId=${NORMAL_ID} saleType=${N_TYPE}"

[[ "$S_TYPE" == "1" ]] && ok "详情 saleType=1 for seckill product" || bad "详情 saleType expected 1 got ${S_TYPE}"
[[ "$N_TYPE" == "0" || "$N_TYPE" == "None" || -z "$N_TYPE" ]] && ok "详情 saleType=0 for normal product" || bad "详情 saleType expected 0 got ${N_TYPE}"

echo "==> Case A: 秒杀商品误走普通下单 → 应拒绝"
RESP=$(curl -s -X POST "${BASE_URL}/api/order" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"productId\":${SECKILL_ID}}")
CODE=$(json_code <<<"$RESP")
MSG=$(python3 -c "import json,sys; print(json.load(sys.stdin).get('message',''))" <<<"$RESP")
[[ "$CODE" != "200" ]] && ok "seckill→/order rejected code=${CODE} msg=${MSG}" || bad "seckill→/order should fail, got ${RESP}"

echo "==> Case B: 普通商品误走秒杀 → 应拒绝"
RESP=$(curl -s -X POST "${BASE_URL}/api/order/seckill" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"productId\":${NORMAL_ID}}")
CODE=$(json_code <<<"$RESP")
MSG=$(python3 -c "import json,sys; print(json.load(sys.stdin).get('message',''))" <<<"$RESP")
[[ "$CODE" != "200" ]] && ok "normal→/seckill rejected code=${CODE} msg=${MSG}" || bad "normal→/seckill should fail, got ${RESP}"

echo "==> Case C: 秒杀商品正确走 /order/seckill → 入队并查结果"
RESP=$(curl -s -X POST "${BASE_URL}/api/order/seckill" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"productId\":${SECKILL_ID}}")
CODE=$(json_code <<<"$RESP")
if [[ "$CODE" == "200" ]]; then
  ok "seckill enqueue ok"
  SUCCESS=0
  for i in $(seq 1 30); do
    sleep 1
    R=$(curl -sf "${BASE_URL}/api/order/seckill/result/${SECKILL_ID}" -H "$AUTH")
    ST=$(python3 -c "import json,sys; print(json.load(sys.stdin)['data'].get('status'))" <<<"$R")
    ON=$(python3 -c "import json,sys; print(json.load(sys.stdin)['data'].get('orderNo') or '')" <<<"$R")
    if [[ "$ST" == "1" ]]; then
      ok "seckill result success orderNo=${ON}"
      SUCCESS=1
      break
    fi
    if [[ "$ST" == "-1" ]]; then
      bad "seckill result failed: ${R}"
      SUCCESS=-1
      break
    fi
  done
  [[ "$SUCCESS" == "0" ]] && bad "seckill still queuing after 30s"
else
  # 可能已秒杀过（幂等/重复），也算校验到了入口可用
  MSG=$(python3 -c "import json,sys; print(json.load(sys.stdin).get('message',''))" <<<"$RESP")
  if echo "$MSG" | grep -qE '重复|已'; then
    ok "seckill endpoint reachable (duplicate request): ${MSG}"
  else
    bad "seckill enqueue failed: ${RESP}"
  fi
fi

echo "==> Case D: 普通商品正确走 /order（可能因库存/限购失败，但不应是「请走秒杀」）"
RESP=$(curl -s -X POST "${BASE_URL}/api/order" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"productId\":${NORMAL_ID}}")
CODE=$(json_code <<<"$RESP")
MSG=$(python3 -c "import json,sys; print(json.load(sys.stdin).get('message',''))" <<<"$RESP")
if [[ "$CODE" == "200" ]]; then
  ok "normal order created"
elif echo "$MSG" | grep -q '秒杀'; then
  bad "normal order wrongly routed to seckill rule: ${MSG}"
else
  ok "normal /order accepted by router (biz reject ok): code=${CODE} msg=${MSG}"
fi

echo
echo "==== Summary: pass=${pass} fail=${fail} ===="
[[ "$fail" -eq 0 ]]

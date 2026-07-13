#!/usr/bin/env bash
# 秒杀压测专用脚本：专门打 POST /api/order/seckill（+ 可选查结果）
#
# 默认直连 campus-gateway（绕过 nginx 限流），适合本机并发压测。
# 商品锚点：productId=1（sale_type=1，seed-perf 高等数学）
# 账号：perfuser001..perfuser1500 / 123456
#
# Usage:
#   ./scripts/bench-seckill.sh
#   ./scripts/bench-seckill.sh --users 1500 --stock 100000 --concurrency 200
#   ./scripts/bench-seckill.sh --base-url http://127.0.0.1:8080   # 走 nginx（易 429）
#   ./scripts/bench-seckill.sh --no-reset --users 50
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [[ ! -f "$ROOT/.env" ]]; then
  echo "Missing .env" >&2
  exit 1
fi
# shellcheck disable=SC1091
source "$ROOT/.env"

USERS=100
STOCK=100
PRODUCT_ID=1
RESET=1
POLL=1
CONCURRENCY=50
BASE_URL=""
WORKDIR="${TMPDIR:-/tmp}/campus-seckill-bench-$$"

usage() {
  sed -n '2,16p' "$0" | sed 's/^# \?//'
  exit 0
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --users) USERS="$2"; shift 2 ;;
    --stock) STOCK="$2"; shift 2 ;;
    --product-id) PRODUCT_ID="$2"; shift 2 ;;
    --concurrency) CONCURRENCY="$2"; shift 2 ;;
    --base-url) BASE_URL="$2"; shift 2 ;;
    --no-reset) RESET=0; shift ;;
    --no-poll) POLL=0; shift ;;
    -h|--help) usage ;;
    *) echo "Unknown arg: $1" >&2; exit 1 ;;
  esac
done

if [[ -z "$BASE_URL" ]]; then
  # Prefer service-net IP (gateway listens there for other services)
  GW_IP=$(docker inspect campus-gateway --format '{{range $name, $conf := .NetworkSettings.Networks}}{{println $name $conf.IPAddress}}{{end}}' \
    | awk '/service-net/ {print $2; found=1} END {if(!found) exit 1}') \
    || GW_IP=$(docker inspect campus-gateway --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{println}}{{end}}' | head -n1)
  BASE_URL="http://${GW_IP}:8080"
  echo "==> BASE_URL=${BASE_URL} (direct gateway, bypass nginx rate limit)"
else
  echo "==> BASE_URL=${BASE_URL}"
fi

if (( USERS > 1500 )); then
  echo "USERS max 1500 (perfuser001..1500)" >&2
  exit 1
fi

mkdir -p "$WORKDIR/tokens" "$WORKDIR/seckill" "$WORKDIR/results"
cleanup() { rm -rf "$WORKDIR"; }
trap cleanup EXIT

echo "==> Target productId=${PRODUCT_ID} users=${USERS} stock=${STOCK} concurrency=${CONCURRENCY}"

if (( RESET == 1 )); then
  echo "==> Reset DB stock/sale_type + Redis seckill keys"
  docker exec campus-mysql mysql --default-character-set=utf8mb4 -ucampus -p"${MYSQL_PASSWORD}" campus_trade -e \
    "UPDATE t_product SET stock=${STOCK}, status=1, sale_type=1 WHERE id=${PRODUCT_ID};
     SELECT id,title,stock,sale_type,status FROM t_product WHERE id=${PRODUCT_ID}\G" >/dev/null
  docker exec campus-redis redis-cli -a "${REDIS_PASSWORD}" --no-auth-warning --scan --pattern "seckill:*${PRODUCT_ID}*" \
    | while read -r key; do
        [[ -n "$key" ]] || continue
        docker exec campus-redis redis-cli -a "${REDIS_PASSWORD}" --no-auth-warning DEL "$key" >/dev/null
      done
  # 详情缓存可能缺 saleType
  docker exec campus-redis redis-cli -a "${REDIS_PASSWORD}" --no-auth-warning DEL "product:detail:${PRODUCT_ID}" >/dev/null || true
fi

DETAIL=$(curl -sf "${BASE_URL}/api/product/${PRODUCT_ID}")
SALE_TYPE=$(python3 -c "import json,sys; print(json.load(sys.stdin)['data'].get('saleType'))" <<<"$DETAIL")
STOCK_NOW=$(python3 -c "import json,sys; print(json.load(sys.stdin)['data'].get('stock'))" <<<"$DETAIL")
echo "    detail saleType=${SALE_TYPE} stock=${STOCK_NOW}"
if [[ "$SALE_TYPE" != "1" ]]; then
  echo "ERROR: product ${PRODUCT_ID} saleType!=1，秒杀入口会拒绝" >&2
  exit 1
fi

echo "==> Prefetch tokens (perfuser001..$(printf '%03d' "$USERS"))"
login_one() {
  local i="$1"
  local user
  user=$(printf 'perfuser%03d' "$i")
  local out="$WORKDIR/tokens/${user}.json"
  local resp
  resp=$(curl -s -X POST "${BASE_URL}/api/user/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"${user}\",\"password\":\"123456\"}") || true
  python3 -c "import json,sys; d=json.load(sys.stdin); assert d.get('code')==200, d; open(sys.argv[1],'w').write(d['data']['token'])" \
    "$out" <<<"$resp"
}
export -f login_one
export BASE_URL WORKDIR
seq 1 "$USERS" | xargs -P "$CONCURRENCY" -I{} bash -c 'login_one "$@"' _ {}
TOKEN_COUNT=$(find "$WORKDIR/tokens" -type f | wc -l)
echo "    tokens ready: ${TOKEN_COUNT}"
[[ "$TOKEN_COUNT" -eq "$USERS" ]] || { echo "login incomplete" >&2; exit 1; }

echo "==> Fire POST /api/order/seckill (productId=${PRODUCT_ID})"
START_NS=$(date +%s%N)
fire_one() {
  local token_file="$1"
  local user
  user=$(basename "$token_file" .json)
  local token
  token=$(cat "$token_file")
  local out="$WORKDIR/seckill/${user}.json"
  curl -s -o "$out" -w "%{http_code}" -X POST "${BASE_URL}/api/order/seckill" \
    -H "Authorization: Bearer ${token}" \
    -H "Content-Type: application/json" \
    -d "{\"productId\":${PRODUCT_ID}}" >"$WORKDIR/seckill/${user}.http"
}
export -f fire_one
export PRODUCT_ID
find "$WORKDIR/tokens" -type f | xargs -P "$CONCURRENCY" -I{} bash -c 'fire_one "$@"' _ {}
END_NS=$(date +%s%N)
ELAPSED_MS=$(( (END_NS - START_NS) / 1000000 ))

python3 - <<'PY' "$WORKDIR" "$ELAPSED_MS" "$USERS"
import json, sys, pathlib
wd = pathlib.Path(sys.argv[1])
elapsed_ms = int(sys.argv[2])
users = int(sys.argv[3])
http_ok = biz_ok = biz_fail = http_err = 0
msgs = {}
for p in (wd / "seckill").glob("*.json"):
    http_code = (wd / "seckill" / (p.stem + ".http")).read_text().strip()
    try:
        d = json.loads(p.read_text() or "{}")
    except Exception:
        http_err += 1
        continue
    if http_code != "200":
        http_err += 1
        msgs[f"http_{http_code}"] = msgs.get(f"http_{http_code}", 0) + 1
        continue
    if d.get("code") == 200:
        biz_ok += 1
    else:
        biz_fail += 1
        m = d.get("message") or str(d.get("code"))
        msgs[m] = msgs.get(m, 0) + 1
print("==== Seckill enqueue summary ====")
print(f"users={users} elapsed_ms={elapsed_ms} qps≈{users / max(elapsed_ms/1000, 0.001):.1f}")
print(f"biz_ok(enqueued)={biz_ok} biz_fail={biz_fail} http_err={http_err}")
if msgs:
    print("top fail reasons:")
    for k, v in sorted(msgs.items(), key=lambda x: -x[1])[:10]:
        print(f"  {v}x  {k}")
open(wd / "summary_enqueue.json", "w").write(json.dumps({
    "biz_ok": biz_ok, "biz_fail": biz_fail, "http_err": http_err, "elapsed_ms": elapsed_ms
}, ensure_ascii=False))
PY

if (( POLL == 1 )); then
  echo "==> Poll GET /api/order/seckill/result/${PRODUCT_ID} (up to 40s)"
  poll_one() {
    local token_file="$1"
    local user
    user=$(basename "$token_file" .json)
    local token
    token=$(cat "$token_file")
    local out="$WORKDIR/results/${user}.json"
    for _ in $(seq 1 40); do
      local resp
      resp=$(curl -s "${BASE_URL}/api/order/seckill/result/${PRODUCT_ID}" \
        -H "Authorization: Bearer ${token}")
      local st
      st=$(python3 -c "import json,sys; d=json.load(sys.stdin); print((d.get('data') or {}).get('status',''))" <<<"$resp" 2>/dev/null || echo "")
      if [[ "$st" == "1" || "$st" == "-1" ]]; then
        echo "$resp" >"$out"
        return 0
      fi
      sleep 1
    done
    echo '{"code":408,"message":"poll timeout","data":{"status":0}}' >"$out"
  }
  export -f poll_one
  # 只轮询入队成功的用户
  for f in "$WORKDIR"/seckill/*.json; do
    user=$(basename "$f" .json)
    code=$(python3 -c "import json,sys; print(json.load(open(sys.argv[1])).get('code'))" "$f" 2>/dev/null || echo 0)
    [[ "$code" == "200" ]] || continue
    echo "$WORKDIR/tokens/${user}.json"
  done | xargs -P "$CONCURRENCY" -I{} bash -c 'poll_one "$@"' _ {}

  python3 - <<'PY' "$WORKDIR" "$PRODUCT_ID"
import json, pathlib, sys
wd = pathlib.Path(sys.argv[1])
ok = fail = timeout = 0
orders = []
for p in (wd / "results").glob("*.json"):
    d = json.loads(p.read_text())
    st = (d.get("data") or {}).get("status")
    if st == 1:
        ok += 1
        orders.append((d["data"].get("orderNo"), p.stem))
    elif st == -1:
        fail += 1
    else:
        timeout += 1
print("==== Seckill result summary ====")
print(f"order_success={ok} order_fail={fail} poll_timeout={timeout}")
if orders[:5]:
    print("sample orders:")
    for on, user in orders[:5]:
        print(f"  {user} -> {on}")
PY
fi

echo "==> DB stock now"
docker exec campus-mysql mysql -N -ucampus -p"${MYSQL_PASSWORD}" campus_trade -e \
  "SELECT CONCAT('product_id=',id,', stock=',stock,', sale_type=',sale_type) FROM t_product WHERE id=${PRODUCT_ID};
   SELECT CONCAT('seckill_orders≈', COUNT(*)) FROM t_order WHERE product_id=${PRODUCT_ID} AND request_id IS NOT NULL;"

echo
echo "接口说明（压测专用）:"
echo "  POST ${BASE_URL}/api/order/seckill          body: {\"productId\":${PRODUCT_ID}}"
echo "  GET  ${BASE_URL}/api/order/seckill/result/${PRODUCT_ID}"
echo "  前提: Authorization: Bearer <token>, 商品 saleType=1"
echo "Done."

#!/usr/bin/env bash
# 检查 Nacos 中是否出现跨机实例（需本机可访问 MW:8848）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck disable=SC1091
source "${ROOT}/.env"

BASE="http://${MW_HOST}:${NACOS_PORT:-8848}/nacos"
echo "Nacos: ${BASE}"
for svc in campus-gateway campus-user campus-product campus-order; do
  echo "---- ${svc} ----"
  curl -fsS "${BASE}/v1/ns/instance/list?serviceName=${svc}" | head -c 2000 || echo "(query failed)"
  echo
done

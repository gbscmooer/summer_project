#!/usr/bin/env bash
# 随机停止本机一个业务容器（不碰中间件）
set -euo pipefail
CANDIDATES=()
for c in campus-product campus-order campus-user campus-gateway; do
  if docker ps --format '{{.Names}}' | grep -qx "$c"; then
    CANDIDATES+=("$c")
  fi
done
if [[ ${#CANDIDATES[@]} -eq 0 ]]; then
  echo "No running app containers on this host."
  exit 1
fi
PICK="${CANDIDATES[$((RANDOM % ${#CANDIDATES[@]}))]}"
echo "Random kill: ${PICK}"
docker stop "${PICK}"
echo "Stopped ${PICK}. Use chaos-start.sh / compose up to recover."

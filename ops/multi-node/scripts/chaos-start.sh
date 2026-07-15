#!/usr/bin/env bash
# 恢复本机业务容器
set -euo pipefail
NAME="${1:?usage: $0 product|order|user|gateway|web}"
case "$NAME" in
  product) C=campus-product ;;
  order) C=campus-order ;;
  user) C=campus-user ;;
  gateway) C=campus-gateway ;;
  web) C=campus-web ;;
  *) echo "unknown: $NAME"; exit 1 ;;
esac
echo "Starting ${C} ..."
docker start "${C}"
echo "Done."

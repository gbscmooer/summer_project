#!/usr/bin/env bash
# 停止本机某个业务容器：product|order|user|gateway|web
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
echo "Stopping ${C} on $(hostname) ..."
docker stop "${C}"
echo "Done. Check Nacos console for instance removal."

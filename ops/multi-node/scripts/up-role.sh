#!/usr/bin/env bash
# 3 台验证一键提示（需在对应角色机器上执行对应分支）
set -euo pipefail
ROLE="${1:?usage: $0 mw|edge|app|app-a|app-b|app-c|data-primary|data-replica}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
[[ -f .env ]] || { echo "Missing .env — copy .env.example first"; exit 1; }

case "$ROLE" in
  mw)
    docker compose -f docker-compose.mw.yml --env-file .env up -d
    ;;
  edge)
    docker compose -f docker-compose.edge.yml --env-file .env up -d
    docker compose -f docker-compose.user.yml --env-file .env up -d
    ;;
  app)
    mkdir -p /data/product-images
    docker compose -f docker-compose.product.yml --env-file .env up -d
    docker compose -f docker-compose.order.yml --env-file .env up -d
    ;;
  app-a)
    mkdir -p /data/product-images
    docker compose -f docker-compose.app-a.yml --env-file .env up -d
    ;;
  app-b)
    mkdir -p /data/product-images
    docker compose -f docker-compose.app-b.yml --env-file .env up -d
    ;;
  app-c)
    docker compose -f docker-compose.app-c.yml --env-file .env up -d
    ;;
  data-primary)
    docker compose -f docker-compose.data-primary.yml --env-file .env up -d
    ;;
  data-replica)
    docker compose -f docker-compose.data-replica.yml --env-file .env up -d
    ;;
  *)
    echo "role must be mw|edge|app|app-a|app-b|app-c|data-primary|data-replica"
    exit 1
    ;;
esac
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'

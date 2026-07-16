#!/usr/bin/env bash
# 静态校验：data-replica 必须与 data-primary 一样使用 DATA_*_DIR bind mount。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPLICA="$ROOT/docker-compose.data-replica.yml"
PRIMARY="$ROOT/docker-compose.data-primary.yml"
FAIL=0
pass() { echo "  OK  $*"; }
fail() { echo "  FAIL $*"; FAIL=1; }

[[ -f "$REPLICA" && -f "$PRIMARY" ]] || { echo "missing compose files"; exit 1; }

if grep -qE 'mysql-replica-data:|redis-replica-data:' "$REPLICA"; then
  fail "data-replica still declares named volumes (ignores DATA_*_DIR / data disk)"
else
  pass "no mysql/redis named volumes on data-replica"
fi

if grep -q '\${DATA_MYSQL_DIR' "$REPLICA" && grep -q '\${DATA_REDIS_DIR' "$REPLICA"; then
  pass "data-replica bind-mounts DATA_MYSQL_DIR and DATA_REDIS_DIR"
else
  fail "data-replica missing DATA_MYSQL_DIR / DATA_REDIS_DIR bind mounts"
fi

if grep -q '\${DATA_MYSQL_DIR' "$PRIMARY" && grep -q '\${DATA_REDIS_DIR' "$PRIMARY"; then
  pass "data-primary still uses DATA_*_DIR (parity check)"
else
  fail "data-primary lost DATA_*_DIR mounts"
fi

if [[ $FAIL -eq 0 ]]; then
  echo "ALL CHECKS PASSED"
  exit 0
fi
echo "SOME CHECKS FAILED"
exit 1

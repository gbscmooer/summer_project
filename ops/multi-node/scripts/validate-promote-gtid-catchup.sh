#!/usr/bin/env bash
# Static check: planned promote must wait for old primary gtid_executed, not only
# Seconds_Behind_Source / RECEIVED_TRANSACTION_SET.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PROMOTE="$ROOT_DIR/scripts/promote-mysql.sh"
FAIL=0

ok() { echo "  OK  $*"; }
fail() { echo "  FAIL $*"; FAIL=1; }

[[ -f "$PROMOTE" ]] || { echo "ERROR: missing $PROMOTE" >&2; exit 1; }

if grep -q "MYSQL_PRIMARY_HOST" "$PROMOTE" \
  && grep -q "@@GLOBAL.gtid_executed" "$PROMOTE" \
  && grep -q "WAIT_FOR_EXECUTED_GTID_SET('\${primary_gtids}'" "$PROMOTE"; then
  ok "planned promote waits on primary gtid_executed"
else
  fail "planned promote must SELECT @@GLOBAL.gtid_executed from MYSQL_PRIMARY_HOST and WAIT_FOR_EXECUTED_GTID_SET"
fi

if grep -q "RECEIVED_TRANSACTION_SET" "$PROMOTE"; then
  ok "still drains RECEIVED_TRANSACTION_SET before RESET (failure path)"
else
  fail "missing RECEIVED_TRANSACTION_SET drain before RESET REPLICA ALL"
fi

if grep -q "failure_promote" "$PROMOTE" && grep -q "FORCE_PROMOTE" "$PROMOTE"; then
  ok "failure promote path still gated by FORCE_PROMOTE"
else
  fail "FORCE_PROMOTE / failure_promote gating missing"
fi

echo ""
if [[ "$FAIL" -eq 0 ]]; then
  echo "validate-promote-gtid-catchup: PASSED"
  exit 0
fi
echo "validate-promote-gtid-catchup: FAILED"
exit 1

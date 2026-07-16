#!/usr/bin/env bash
# Static checks for data-primary / data-replica HA correctness.
# Run from repo: ops/multi-node/scripts/validate-data-ha-config.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PRIMARY="$ROOT_DIR/docker-compose.data-primary.yml"
REPLICA="$ROOT_DIR/docker-compose.data-replica.yml"
REPLICA_CNF="$ROOT_DIR/mysql/replica.cnf"
FAIL=0

fail() {
  echo "FAIL: $*" >&2
  FAIL=1
}

pass() {
  echo "OK: $*"
}

# Sentinel master name defaults must match (quorum=2).
primary_default="$(grep -E 'REDIS_SENTINEL_MASTER_NAME:.*:-' "$PRIMARY" | head -1 || true)"
replica_default="$(grep -E 'REDIS_SENTINEL_MASTER_NAME:.*:-' "$REPLICA" | head -1 || true)"
if [[ "$primary_default" == *":-mymaster}"* && "$replica_default" == *":-mymaster}"* ]]; then
  pass "sentinel master defaults both mymaster"
else
  fail "sentinel master defaults diverge: primary='$primary_default' replica='$replica_default'"
fi

if grep -q 'skip_replica_start\s*=\s*ON' "$REPLICA_CNF"; then
  fail "replica.cnf has skip_replica_start=ON (replication will not resume after restart)"
else
  pass "replica.cnf does not skip replica start"
fi

if grep -q '.campus-super-read-only' "$REPLICA" \
  && grep -q 'super-read-only=ON' "$REPLICA" \
  && grep -q '.campus-super-read-only' "$ROOT_DIR/scripts/enable-mysql-readonly.sh" \
  && grep -q '.campus-super-read-only' "$ROOT_DIR/scripts/promote-mysql.sh"; then
  pass "restart-persistent read_only marker wired in compose + scripts"
else
  fail "missing .campus-super-read-only marker wiring for restart-safe read_only"
fi

if grep -q 'replica already running' "$ROOT_DIR/scripts/setup-replication.sh" \
  && grep -q 'START REPLICA (no RESET)' "$ROOT_DIR/scripts/setup-replication.sh"; then
  pass "setup-replication.sh is idempotent for existing channels"
else
  fail "setup-replication.sh missing idempotent paths (risk of RESET MASTER on re-run)"
fi

if command -v docker >/dev/null 2>&1; then
  # Compose config interpolation requires the required env vars to be present.
  export MYSQL_ROOT_PASSWORD=test \
    MYSQL_PASSWORD=test \
    MYSQL_REPLICATION_PASSWORD=test \
    REDIS_PASSWORD=test \
    HOST_IP=127.0.0.1 \
    REDIS_PRIMARY_HOST=127.0.0.1
  if docker compose -f "$PRIMARY" config >/dev/null \
    && docker compose -f "$REPLICA" config >/dev/null; then
    pass "docker compose config parses for primary and replica"
  else
    fail "docker compose config failed"
  fi
else
  echo "SKIP: docker not available for compose config check"
fi

if [[ "$FAIL" -ne 0 ]]; then
  echo "validate-data-ha-config: FAILED" >&2
  exit 1
fi
echo "validate-data-ha-config: PASSED"

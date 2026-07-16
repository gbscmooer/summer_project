#!/usr/bin/env bash
# 在 Node7（数据主）上：创建复制账号，供 Node8 setup-replication.sh 使用。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
[[ -f .env ]] || { echo "Missing .env"; exit 1; }
# shellcheck disable=SC1091
set -a; source .env; set +a

MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:?required}"
REPL_USER="${MYSQL_REPL_USER:-repl}"
REPL_PASSWORD="${MYSQL_REPL_PASSWORD:-$MYSQL_ROOT_PASSWORD}"

echo "==> Wait for campus-mysql-primary healthy"
for i in $(seq 1 60); do
  if docker exec campus-mysql-primary mysqladmin ping -h localhost -uroot -p"${MYSQL_ROOT_PASSWORD}" --silent 2>/dev/null; then
    break
  fi
  sleep 2
  [[ $i -eq 60 ]] && { echo "MySQL primary not ready"; exit 1; }
done

docker exec -i campus-mysql-primary mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE USER IF NOT EXISTS '${REPL_USER}'@'%' IDENTIFIED WITH mysql_native_password BY '${REPL_PASSWORD}';
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO '${REPL_USER}'@'%';
FLUSH PRIVILEGES;
SHOW MASTER STATUS\\G
SELECT user, host FROM mysql.user WHERE user='${REPL_USER}';
SQL

echo "==> Replication user ready. Binlog/GTID enabled via primary.cnf"

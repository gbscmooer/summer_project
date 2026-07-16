#!/bin/bash
# Creates MySQL replication user on primary (runs once via docker-entrypoint-initdb.d).
set -euo pipefail
mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<EOSQL
CREATE USER IF NOT EXISTS 'repl'@'%' IDENTIFIED BY '${MYSQL_REPLICATION_PASSWORD}';
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'repl'@'%';
FLUSH PRIVILEGES;
EOSQL

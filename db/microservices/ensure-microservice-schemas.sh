#!/bin/sh
set -eu

MYSQL_HOST="${MYSQL_HOST:-mysql}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_ROOT_PASSWORD:-${MYSQL_PASSWORD:-123456}}"
SCHEMA_SQL="${SCHEMA_SQL:-/db/init-microservice-schemas.sql}"

if [ ! -f "$SCHEMA_SQL" ]; then
    echo "Schema SQL not found: $SCHEMA_SQL" >&2
    exit 1
fi

tmp_sql="$(mktemp)"
trap 'rm -f "$tmp_sql"' EXIT

sed \
    -e 's/\r$//' \
    -e '/^DROP TABLE IF EXISTS /d' \
    -e 's/^CREATE TABLE `/CREATE TABLE IF NOT EXISTS `/' \
    -e 's/^INSERT INTO `/INSERT IGNORE INTO `/' \
    "$SCHEMA_SQL" > "$tmp_sql"

echo "Ensuring microservice databases and tables on ${MYSQL_HOST}:${MYSQL_PORT}..."
mysql \
    --default-character-set=utf8mb4 \
    --protocol=TCP \
    --host="$MYSQL_HOST" \
    --port="$MYSQL_PORT" \
    --user="$MYSQL_USER" \
    --password="$MYSQL_PASSWORD" \
    < "$tmp_sql"
echo "Microservice database ensure completed."

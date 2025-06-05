#!/bin/bash
echo "[INIT_CASSANDRA_SCRIPT] >>> Script execution started at $(date)"

set -e
set -x

CASSANDRA_HOST="cassandra"
CASSANDRA_PORT="9042"
KEYSPACE_NAME="stock_keyspace"

echo "[INIT_CASSANDRA_SCRIPT] Waiting for Cassandra to be ready at $CASSANDRA_HOST:$CASSANDRA_PORT..."
retry_count=0
max_retries=30
until cqlsh "$CASSANDRA_HOST" "$CASSANDRA_PORT" -e "describe keyspaces;" &>/dev/null; do
  retry_count=$((retry_count+1))
  if [ "$retry_count" -gt "$max_retries" ]; then
    echo "[INIT_CASSANDRA_SCRIPT] Cassandra did not become available after $max_retries attempts. Exiting."
    exit 1
  fi
  >&2 echo "[INIT_CASSANDRA_SCRIPT] Cassandra is unavailable - sleeping for 5 seconds (attempt $retry_count/$max_retries)"
  sleep 5
done

echo "[INIT_CASSANDRA_SCRIPT] Cassandra is up - ensuring keyspace '$KEYSPACE_NAME' and tables exist..."

cqlsh "$CASSANDRA_HOST" "$CASSANDRA_PORT" -e "CREATE KEYSPACE IF NOT EXISTS $KEYSPACE_NAME WITH replication = {'class': 'SimpleStrategy', 'replication_factor': '1'} AND durable_writes = true;"

cqlsh "$CASSANDRA_HOST" "$CASSANDRA_PORT" <<EOF
USE $KEYSPACE_NAME;

CREATE TABLE IF NOT EXISTS stock_summaries (
    symbol text PRIMARY KEY,
    last_timestamp bigint,
    current_price double,
    latest_volume bigint,
    latest_volatility double,
    latest_risk_score double,
    latest_trend text,
    calculation_date text,
    price_change_today double,
    price_change_percent_today double
);

CREATE TABLE IF NOT EXISTS economic_indicator_summaries (
    indicator text PRIMARY KEY,
    last_timestamp bigint,
    latest_value double,
    observation_date text
);

CREATE TABLE IF NOT EXISTS market_news (
    id uuid,
    date_bucket text,
    timestamp bigint,
    headline text,
    summary text,
    sentiment text,
    source text,
    related_symbol text,
    PRIMARY KEY (date_bucket, timestamp, id)
) WITH CLUSTERING ORDER BY (timestamp DESC, id ASC);

CREATE TABLE IF NOT EXISTS economic_indicator_metadata (
    series_id text PRIMARY KEY,
    title text,
    frequency text,
    units text,
    source text,
    last_updated timestamp
);
EOF

>&2 echo "[INIT_CASSANDRA_SCRIPT] Cassandra keyspace '$KEYSPACE_NAME' and tables ensured."
echo "[INIT_CASSANDRA_SCRIPT] --- Script finished successfully at $(date) ---"
exit 0
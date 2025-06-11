#!/bin/bash
set -e

echo "[INIT_INFLUXDB_SCRIPT] >>> Starting InfluxDB initialization..."

if [ -z "$INFLUXDB_URL" ] || [ -z "$INFLUXDB_TOKEN" ] || [ -z "$INFLUXDB_ORG" ] || [ -z "$INFLUXDB_BUCKET" ]; then
  echo "[INIT_INFLUXDB_SCRIPT] ERROR: One or more required environment variables are not set."
  exit 1
fi

echo "[INIT_INFLUXDB_SCRIPT] Waiting for InfluxDB to be ready at ${INFLUXDB_URL}..."
until curl -s -o /dev/null -w "%{http_code}" "${INFLUXDB_URL}/ping" | grep -q "204"; do
  >&2 echo "[INIT_INFLUXDB_SCRIPT] InfluxDB is unavailable - sleeping for 2 seconds"
  sleep 2
done
echo "[INIT_INFLUXDB_SCRIPT] InfluxDB is up."

INFLUX_CLI_CONFIG_NAME="init-config-$$"

echo "[INIT_INFLUXDB_SCRIPT] Setting up temporary Influx CLI config..."
influx config create --config-name ${INFLUX_CLI_CONFIG_NAME} \
  --host-url ${INFLUXDB_URL} \
  --org ${INFLUXDB_ORG} \
  --token ${INFLUXDB_TOKEN} \
  --active

echo "[INIT_INFLUXDB_SCRIPT] Finding bucket ID for '${INFLUXDB_BUCKET}'..."
BUCKET_ID=$(influx bucket find --name ${INFLUXDB_BUCKET} --hide-headers | awk '{print $1}')

if [ -z "$BUCKET_ID" ]; then
  echo "[INIT_INFLUXDB_SCRIPT] ERROR: Could not find bucket '${INFLUXDB_BUCKET}'."
  exit 1
fi

echo "[INIT_INFLUXDB_SCRIPT] Found Bucket ID: ${BUCKET_ID}. Updating retention policy to infinite..."
influx bucket update --id ${BUCKET_ID} --retention 0

echo "[INIT_INFLUXDB_SCRIPT] --- Retention policy updated successfully ---"
exit 0

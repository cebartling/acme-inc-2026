#!/usr/bin/env bash
# Runs as a one-off ECS task in the private subnets (db-init.tf), with the services security
# group. It does two things, and exits non-zero if either fails:
#   1. checks that RDS, DocumentDB, ElastiCache and MSK accept connections;
#   2. creates the service databases in RDS if they don't exist.
# Flyway creates the tables when each service starts.
#
# Environment: POSTGRES_HOST, POSTGRES_PORT, POSTGRES_USER, PGPASSWORD, SERVICE_DATABASES
# (space-separated), DOCDB_HOST, DOCDB_PORT, REDIS_HOST, REDIS_PORT, KAFKA_BROKERS
# (comma-separated host:port).
set -euo pipefail

# A fresh endpoint can take a moment to resolve, so retry for about a minute.
reachable() {
  local host="$1" port="$2" attempt
  for attempt in $(seq 1 10); do
    if timeout 5 bash -c "exec 3<>/dev/tcp/$host/$port"; then
      echo "reachable: $host:$port"
      return 0
    fi
    sleep 6
  done
  echo "UNREACHABLE: $host:$port" >&2
  return 1
}

reachable "$POSTGRES_HOST" "$POSTGRES_PORT"
reachable "$DOCDB_HOST" "$DOCDB_PORT"
reachable "$REDIS_HOST" "$REDIS_PORT"
for broker in ${KAFKA_BROKERS//,/ }; do
  reachable "${broker%%:*}" "${broker##*:}"
done

export PGHOST="$POSTGRES_HOST" PGPORT="$POSTGRES_PORT" PGUSER="$POSTGRES_USER" PGDATABASE=postgres
for database in $SERVICE_DATABASES; do
  if psql -tAc "SELECT 1 FROM pg_database WHERE datname = '$database'" | grep -q 1; then
    echo "database exists: $database"
  else
    psql -c "CREATE DATABASE $database"
  fi
done

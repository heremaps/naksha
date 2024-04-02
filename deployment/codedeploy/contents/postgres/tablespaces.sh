#!/bin/bash
databaseName=postgres
tempTablespacePath="/tmp/temp_tablespace"
echo "tempTablespacePath: $tempTablespacePath"
echo "create catalog $tempTablespacePath"
mkdir -p "$tempTablespacePath"

chown postgres:postgres -R $tempTablespacePath

echo "Creating tablespace for temp tables ..."
psql -U postgres -d $databaseName -c "create tablespace temp LOCATION '${tempTablespacePath}';" || echo 'tablespace exists, skipping the error'

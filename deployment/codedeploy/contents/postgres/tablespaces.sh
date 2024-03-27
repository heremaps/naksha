#!/bin/bash
databaseName=postgres
arenaId="test_arena"
rootPath="/tmp/$arenaId"
headPath="$rootPath/head"
hstPath="$rootPath/hst"
echo "rootPath: $rootPath"
echo "create catalog $rootPath/main"
mkdir -p "$rootPath/main"

for i in {0..7}
do
  echo "create catalog $headPath$i and $hstPath$i"
  mkdir -p "$headPath$i"
  mkdir -p "$hstPath$i"
done

chown postgres:postgres -R $rootPath

echo "Creating tablespace naksha_${arenaId}_main ..."
psql -U postgres -d $databaseName -c "create tablespace naksha_${arenaId}_main LOCATION '/tmp/${arenaId}/main';" || echo 'tablespace exists, skipping the error'

for i in {0..7}
do
  echo "Creating tablespace naksha_${arenaId}_head_$i ..."
  psql -U postgres -d $databaseName -c "create tablespace naksha_${arenaId}_head_$i LOCATION '/tmp/${arenaId}/head$i'" || echo 'tablespace exists, skipping the error'
  echo "Creating tablespace naksha_${arenaId}_hst_$i ..."
  psql -U postgres -d $databaseName -c "create tablespace naksha_${arenaId}_hst_$i LOCATION '/tmp/${arenaId}/hst$i';" || echo 'tablespace exists, skipping the error'
done

#!/bin/bash
storageId="unit_test_moderator"
rootPath="/tmp/$storageId"
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
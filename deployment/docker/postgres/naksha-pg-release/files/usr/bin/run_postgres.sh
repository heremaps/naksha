#!/bin/bash

if [ "$PGDATA" == "" ]; then
  export PGDATA=/usr/local/pgsql/data
fi
if [ "$PGTEMP" == "" ]; then
  export PGTEMP=/usr/local/pgsql/temp
fi

# Setup database
mkdir -p "$PGDATA"
mkdir -p "$PGTEMP"

chown postgres:postgres "$PGDATA" "$PGTEMP"
if [ -z "$(ls -A "$PGDATA")" ]; then
  LC_CTYPE=C tr -dc A-Za-z < /dev/urandom | head -c 32 | xargs > /home/postgres/postgres.pwd
  su postgres -c "initdb --pgdata=$PGDATA --allow-group-access --locale=C.UTF-8 --locale-provider=libc --wal-segsize=1024 -U postgres --pwfile=/home/postgres/postgres.pwd"
  if [ $? == 0 ]; then
    PG_PASSWORD=$(cat /home/postgres/postgres.pwd)
    echo "Initialized database with password: $PG_PASSWORD"
    echo "Copy postgresql.conf to $PGDATA"
    cp /home/postgres/postgresql.conf "$PGDATA"/.
    echo "Start postgres"
    su postgres -c "pg_ctl -D '$PGDATA' start"
    sleep 1
    echo "Create unimap database"
    su postgres -c "psql -c \"CREATE DATABASE unimap;\""
    echo "Create temporary tablespace"
    su postgres -c "psql -c \"CREATE TABLESPACE temp LOCATION '$PGTEMP';\""
    #su postgres -c "psql -d unimap -c \"\set AUTOCOMMIT ON\;CREATE TABLESPACE temp LOCATION '/usr/local/pgsql/temp'\""
    echo "Stop postgres"
    su postgres -c "pg_ctl -D '$PGDATA' stop"
  else
    echo "Failed to initialize database"
    exit 1
  fi
else
  echo "Database already initialized"
  #su postgres -c "pg_ctl -D $PGDATA start"
fi

#POSTMASTER_PID=$(head -n 1 "$PGDATA"/postmaster.pid)
#function clean_shutdown() {
#  if ps -p "$POSTMASTER_PID" > /dev/null; then
#    kill -s SIGTERM "$POSTMASTER_PID";
#    wait "$POSTMASTER_PID";
#  fi
#  exit 0;
#}
#function reload_config() {
#  if ps -p "$POSTMASTER_PID" > /dev/null; then
#    pg_ctl reload
#  fi
#}
#function check_postgres() {
#  if ! ps -p "$POSTMASTER_PID" > /dev/null; then
#    exit 1;
#  fi
#}

#trap "{ echo Received SIGTERM; clean_shutdown; }" SIGTERM
#trap "{ echo Received SIGINT; clean_shutdown; }" SIGINT
#trap "{ echo Received SIGHUP; reload_config; }" SIGHUP

# Wait for signals
#while true; do
#  check_postgres
#  sleep 1000;
#done

# The exec is necessary to for postgres as child so that it receives signals
# Without this, postgres will not cleanly shut down, when docker stop is invoked
echo "Start postgres"
exec su postgres -c "/usr/local/bin/postgres -D '$PGDATA'"

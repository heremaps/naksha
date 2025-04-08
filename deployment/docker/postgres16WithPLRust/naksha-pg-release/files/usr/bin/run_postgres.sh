#!/bin/bash

if [ "$PGDATA" == "" ]; then
  export PGDATA=/usr/local/pgsql/data
fi
if [ "$PGTEMP" == "" ]; then
  export PGTEMP=/usr/local/pgsql/temp
fi
if [ "$PGDATABASE" == "" ]; then
  export PGDATABASE="postgres"
fi
if [ "$PGUSER" == "" ]; then
  export PGUSER="postgres"
fi
if [ "$PGPASSWORD" == "" ]; then
  export PGPASSWORD=$(LC_CTYPE=C tr -dc A-Za-z < /dev/urandom | head -c 32 | xargs)
  STORE_PG_PWD="yes"
else
  STORE_PG_PWD="no"
fi

# Setup database
mkdir -p "$PGDATA"
mkdir -p "$PGTEMP"

chown postgres:postgres "$PGDATA" "$PGTEMP"
if [ -z "$(ls -A "$PGDATA")" ]; then
  echo "$PGPASSWORD" > /home/postgres/postgres.pwd
  chown postgres:postgres /home/postgres/postgres.pwd
  su postgres -c "initdb --pgdata=$PGDATA --allow-group-access --locale=C.UTF-8 --locale-provider=libc --wal-segsize=1024 -U $PGUSER --pwfile=/home/postgres/postgres.pwd"
  if [ $? == 0 ]; then
    echo "Initialized database with password: $PG_PWD"
    echo "Copy postgresql.conf to $PGDATA"
    cp /home/postgres/postgresql.conf "$PGDATA"/.
    echo "Start postgres"
    su postgres -c "pg_ctl -D '$PGDATA' start"
    sleep 1
    # Note: "postgres" is hardcoded in initdb source, therefore we need to create an additional database, if wished
    if [ "postgres" != "$PGDATABASE" ]; then
      echo "Create $PGDATABASE database"
      su postgres -c "psql -d postgres -c \"CREATE DATABASE $PGDATABASE;\""
    fi
    echo "Create temporary tablespace"
    su postgres -c "psql -c \"CREATE TABLESPACE temp LOCATION '$PGTEMP';\""
    echo "Stop postgres"
    su postgres -c "pg_ctl -D '$PGDATA' stop"
    if [ "$STORE_PG_PWD" == "yes" ]; then
      echo "Export generated password in $PGDATA/$PGUSER.pwd"
      echo "$PGPASSWORD" > "$PGDATA/$PGUSER.pwd"
      chown postgres:postgres "$PGDATA/$PGUSER.pwd"
    fi
  else
    echo "Failed to initialize database"
    exit 1
  fi
else
  echo "Database already initialized"
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

# Concept
All we need is:
1. Very fast disk for temp-store. Tables in temp-store should be placed in separate tablespace and marked as `UNLOGGED`.
2. Optimized disk for other purposes (pg_default)

## Volumes separation
On production, we'd like to have such volumes:

| TABLESPACE name                | disk path                                         | naksha objects                                                                                |
|--------------------------------|---------------------------------------------------|-----------------------------------------------------------------------------------------------|
| pg_default                     | $PGDATA (something like: /mnt/{ebs_logic_volume}) | all objects not marked as `temporary`                                                         |
| temp                           | /tmp/temp_tablespace                              | objects marked as `temporary` excluding sequences                                             |

## Postgres (system/docker) configuration
Postgres has to work in both scenarios:
1. storing all data on default system disk, use case: unit tests
    To do this we don't have to do any extra work, it's enough to not specify extra tablespace for tables - all tables will end up in $PGDATA.
2. storing data on separate volumes.
   1. The volumes have to be attached to system under paths specified in "Volumes separation"
   2. The postgres has to have read/write rights to attached volumes
   3. All table-spaces defined in "Volumes separation" have to be created before running Naksha/tests


## Docker 
### Expected solution
At the end we will have a docker image that contains all the catalogs for TABLESPACEs so during `docker run` we could mount volumes to them like this `docker run -v vol0:/tmp/temp_tablespace postgres_image`, and also docker image will execute TABLESPACE creation.

### Temporary solution
At the moment we can create catalogs and tablespaces "by hand"

First, run docker with postgres image you always do:
```
docker run -p 5432:5432 -e ... \
 -e PGDATA=/path_to_default_pg_data_place
 -v /path_to_mounted_catalog_of_super_fast_disk_for_temp_store:/tmp/temp_tablespace \
 postgres
```
NOTICE: postgres has to be owner of `path_to_default_pg_data_place`

Second (postgres should be up and ready), go to `deployment/codedeploy/postgres/tablespaces.sh` script and set proper `databaseName=`. Next execute command:
```
cat  deployment/codedeploy/contents/postgres/tablespaces.sh | docker exec -i {container_id}  bash
```
This will create all required sub-catalogs necessary tablespace.

Now you can run Naksha/unit_tests/integration_tests and execute `naksha.init()`, create collections or anything else .  

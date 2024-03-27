# Concept
In order to maximize utilization of I/O vs CPU we want to split work of storing data into multiple volumes.
Thanks to that we can attach multiple volumes to the system and use their I/O limits.
To achieve that we need to be able to write to separate volumes from separate threads. <br>
In general we want to have 8 head partitions (head[0..7]) and 8 hst partitions per year (i.e.hst_2024_[0..7]).<br>
With such configuration we can place each partition on different volume so when the postgres is storing data into partition, the disk I/O is not a bottleneck anymore.

## Volumes separation
On production, we'd like to have such volumes:

| TABLESPACE name                 | disk path                 | naksha objects                                                                                                    |
|---------------------------------|---------------------------|-------------------------------------------------------------------------------------------------------------------|
| naksha\_{arenaId}\_main         | /tmp/{arenaId}/main       | all head tables (not partitions), indices(only for not-partitioned tables), hst main tables, hst year partitions, |
| naksha\_{arenaId}\_head\_[0..7] | /tmp/{arenaId}/head[0..7] | head partitions (i.e. topology_p0, topology_p1), indices of head partitions                                       |
| naksha\_{arenaId}\_hst\_[0..7]  | /tmp/{arenaId}/hst[0..7]  | history partitions (i.e. topology_hst_2024_0, topology_hst_2024_1), indices of hst partitions                     |
| pg_default                      | $PGDATA                   | naksha_collections table, transaction table                                                                       |

## Postgres (system/docker) configuration
Postgres has to work in both scenarios
1. storing all data on default system disk, use case: unit tests
    To do this we don't have to do any extra work, it's enough to not specify extra tablespace for tables - all tables will end up in $PGDATA.
2. storing data on separate volumes.
    This is a little bit more complicated and has some requirements:
   1. The volumes have to be attached to system under paths specified in "Volumes separation"
   2. The postgres has to have read/write rights to attached volumes
   3. All table-spaces defined in "Volumes separation" have to be created before running Naksha/tests

## Current lib-* behaviour
Currently, at runtime library will check if required tablespace exists for each arena:
1. If true - it will add " TABLESPACE $tablespace" to "CREATE" queries
2. If false - it will leave "CREATE" queries without TABLESPACE specified

## Docker 
### Expected solution
At the end we will have a docker image that contains all the catalogs for TABLESPACEs so during `docker run` we could mount volumes to them like this `docker run -v vol0:/tmp/consistent_store/main postgres_image`, and also docker image will execute TABLESPACE creation  for every defined {arenaId}.

### Temporary solution
At the moment we can create catalogs and tablespaces "by hand"

First, run docker with postgres image you always do:
```
docker run -p 5432:5432 -e ... postgres
```
(optional) mount external volumes to `/tmp/{arenaId}` (this could be made in `docker run ...` command), if you omit this step the postgres will write tables on current volume.

Second (postgres should be up and ready), go to `deployment/codedeploy/postgres/tablespaces.sh` script and set the `arenaId=` value to the value you want, also set proper `databaseName=`. Next execute command:
```
cat  deployment/codedeploy/contents/postgres/tablespaces.sh | docker exec -i {container_id}  bash
```
This will create all required sub-catalogs for arena and create necessary tablespaces.

Now you can run Naksha/unit_tests/integration_tests and execute `naksha.init()`, create collections or anything else .  

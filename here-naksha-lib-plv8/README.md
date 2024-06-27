# Introduction

## Disable IntelliJ Auto-Formatter
Please, disable IntelliJ Auto-Formatter, it sucks unbelievable:
- Open (File|IntellilJ IDEA on Mac)
- Settings
- Editor
- Code Style
- SQL
- General
- Check "Disable formatting"

## Multi-Platform implementation details 
The `lib-psql` currently is supported in the JVM and in the PostgresQL database via [PLV8 extension](https://plv8.github.io/), but can 
be extended on demand to other targets. The main business logic is implemented as multi-platform Kotlin code in 
[commonMain](./src/commonMain/kotlin/naksha/psql), so that basically all features are available in Java and inside the PostgresQL database.

The main code is implemented in [NakshaSession](./src/commonMain/kotlin/naksha/psql/NakshaSession.kt). It represents a storage session 
and implements support for `IReadSession` and `IWriteSession` as specified in `lib-model`. An instance of it is created by providing a 
[PgStorage](./src/commonMain/kotlin/naksha/psql/PgStorage.kt) in the constructor, which is a platform specific implementation.

The [PgStorage](./src/commonMain/kotlin/naksha/psql/PgStorage.kt) extends the `IStorage` interface with just one additional method, that 
allows to open a PostgresQL database connection from a connection pool of the storage.

The [NakshaSession](./src/commonMain/kotlin/naksha/psql/NakshaSession.kt) is not created directly, but returned by the platform specific 
implementation of the [PgStorage](./src/commonMain/kotlin/naksha/psql/PgStorage.kt) interface.

In Java, the following JVM only classes actually provide the [PgStorage](./src/commonMain/kotlin/naksha/psql/PgStorage.kt) implementation:

- The [PsqlStorage](./src/jvmMain/kotlin/naksha/psql/PsqlStorage.kt) directly implements the
  [PgStorage](./src/commonMain/kotlin/naksha/psql/PgStorage.kt) interface, it requires to provide a
  [PsqlCluster](./src/jvmMain/kotlin/naksha/psql/PsqlCluster.kt) to the constructor, when creating an instance of it.
- The [PsqlCluster](./src/jvmMain/kotlin/naksha/psql/PsqlCluster.kt) can be created by providing at least one
  [PsqlInstance](./src/jvmMain/kotlin/naksha/psql/PsqlInstance.kt), representing the master node of the PostgresQL database. It is
  queried via a static getter (`Psqllnstance.get(url|...)`). In the background each instance holds an own dedicate connection pool, and
  the same instance (identifier by host, port, db, user and password) is kept in memory as singleton, so that all clusters and storages
  share the same connection pool. This reduces the amount of connections being created and kept alive.

In [PLV8](https://plv8.github.io/) the [PgStorage](./src/commonMain/kotlin/naksha/psql/PgStorage.kt) is implemented as static member of 
the standard `plv8` object provided out-of-the-box by the [PLV8 extension](https://plv8.github.io/). So, when the SQL function 
`naksha_start_session()` is executed, it will create a [NakshaSession](./src/commonMain/kotlin/naksha/psql/NakshaSession.kt), forwarding 
the necessary parameters, and store it in `plv8.nakshaSession`. When any other SQL function is executed, it will refer to this member 
and throw an SQL exception, when the member does not exist, which guarantees, that `naksha_start_session()` is executed before any other 
code is executed. The method `openSession`, of the [PgStorage](./src/commonMain/kotlin/naksha/psql/PgStorage.kt) interface, is 
implemented in PLV8 as a thin wrapper around the native [SPI](https://plv8.github.io/#database-access-via-spi)-API, provided 
out-of-the-box by the [PLV8 extension](https://plv8.github.io/). Actually, this implementation therefore does only support a single 
connection, so inside the database the parallel optimizations (`executeParallel`) are not supported.

# Things we need to remember
Due to partitioning of the big tables, we need to change **cluster** and **group** parameters:

- `show max_parallel_workers = 16`
- `show max_parallel_workers_per_gather = 16`
  - We split HEAD table into 16 partitions for big data collections, therefore we should be able to query them in parallel.
- `show max_worker_processes = 1024`
  - We should be able to at least execute 64 parallel big table queries.

# Configuration
- https://www.postgresql.org/docs/current/runtime-config-resource.html#GUC-MAX-PARALLEL-WORKERS
- `max_parallel_maintenance_workers = GREATEST({DBInstanceVCPU},64)`
  - Sets the maximum number of parallel workers that can be started by a single utility command.
- `force_parallel_mode = off`
- `effective_io_concurrency = GREATEST({DBInstanceVCPU},1000)`
  - Raising this value will increase the number of I/O operations that any individual PostgreSQL session attempts to initiate in parallel.
- `max_worker_processes = GREATEST({DBInstanceVCPU*4},128)`
  - Sets the maximum number of workers that the system can support for parallel operations.
- `max_parallel_workers = GREATEST({DBInstanceVCPU*4},128)`
  - Sets the maximum number of background processes that the system can support.
  - Limited by `max_worker_processes`.
- `max_parallel_workers_per_gather = GREATEST({DBInstanceVCPU},64)`
  - Sets the maximum number of workers that can be started by a single Gather or Gather Merge node.
  - Parallel workers are taken from the pool of processes established by `max_worker_processes`, limited by `max_parallel_workers`.
- `parallel_leader_participation = off`
  - Allows the leader process to execute the query plan under Gather and Gather Merge nodes instead of waiting for worker processes.
  - Setting this value to off reduces the likelihood that workers will become blocked because the leader is not reading tuples fast enough.

# Links

## Functions
- [Locking](https://www.postgresql.org/docs/current/functions-admin.html#FUNCTIONS-ADVISORY-LOCKS)
- [Date/Time](https://www.postgresql.org/docs/current/functions-datetime.html#FUNCTIONS-DATETIME-CURRENT)
- [String Formatting](https://www.postgresql.org/docs/current/functions-formatting.html)

## PostGIS Functions
- https://postgis.net/docs/ST_Centroid.html

## Details about error handling:
- https://www.postgresql.org/docs/current/plpgsql-errors-and-messages.html
- https://www.postgresql.org/docs/current/errcodes-appendix.html
- https://www.postgresql.org/docs/current/plpgsql-control-structures.html#PLPGSQL-ERROR-TRAPPING

## Index optimization (pg_hint_plan)
- https://www.postgresql.org/docs/current/sql-createindex.html#SQL-CREATEINDEX-STORAGE-PARAMETERS
- https://pg-hint-plan.readthedocs.io/en/master/hint_list.html

## Concurrency & Function Volatility Categories
- https://www.postgresql.org/docs/current/explicit-locking.html
- https://www.postgresql.org/docs/current/xfunc-volatility.html

In a nutshell:

- A **VOLATILE** function can do anything, including modifying the database. It can return different results on successive calls with the same arguments. The optimizer makes no assumptions about the behavior of such functions. A query using a volatile function will re-evaluate the function at every row where its value is needed.
- A **STABLE** function cannot modify the database and is guaranteed to return the same results given the same arguments for all rows within a single statement. This category allows the optimizer to optimize multiple calls of the function to a single call. In particular, it is safe to use an expression containing such a function in an index scan condition. (Since an index scan will evaluate the comparison value only once, not once at each row, it is not valid to use a VOLATILE function in an index scan condition.)
- An **IMMUTABLE** function cannot modify the database and is guaranteed to return the same results given the same arguments forever. This category allows the optimizer to pre-evaluate the function when a query calls it with constant arguments. For example, a query like SELECT ... WHERE x = 2 + 2 can be simplified on sight to SELECT ... WHERE x = 4, because the function underlying the integer addition operator is marked IMMUTABLE.

## Other useful information about PostgesQL
- https://www.postgresql.org/docs/current/catalog-pg-class.html
- https://www.postgresql.org/docs/current/catalog-pg-trigger.html
- https://www.crunchydata.com/blog/random-geometry-generation-with-postgis

# DBeaver - Notification / Debugging
Debugging in DBeaver can be done by adding notices like:

```sql
RAISE NOTICE 'Hello';
```

See: https://www.postgresql.org/docs/16/sql-notify.html

To show the notifications switch to Output tab (Ctrl+Shift+O).

## Session / Transaction

Every connection to PostgresQL starts a new session as soon as the client authenticates. The session is wired to the underlying socket / connection. Within each session there can only be exactly one transaction at a given time. Therefore, there is a 1:1:1 relation between connection, session and transaction.

This is important to understand the configuration values. They are by default bound to the session, so `SET "naksha.x" TO 'Hello World';` will be sticky for the whole session, while `SET LOCAL "naksha.x" TO 'Hello World';` will only be available until the current transaction is either rolled-back or committed. This is important for caching!

Therefore: The effects of SET LOCAL last only till the end of the current transaction.

# Helpers

To insert some random data into do this:

```sql
WITH rnd AS (select md5(random()::text) as id, ST_Force3D(ST_GeneratePoints('POLYGON((0 0, 10 0, 10 10, 0 10, 0 0))', 10)) as g from generate_Series(1,5000))
INSERT INTO foo (jsondata,geo) SELECT ('{"id":"'||id||'"}')::jsonb as jsondata, g FROM rnd;
```

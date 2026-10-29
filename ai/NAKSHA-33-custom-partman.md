# Custom Partition Maintainer Prototype

Install with `psql -X -f ai/NAKSHA-33-custom-partman.sql`. The separate
`NAKSHA-33-custom-partman-test.sql` installs it and commits an inspectable example
in a new `naksha_custom_test` schema. The test refuses to overwrite an existing
schema. Neither script clears the database or depends on pg_partman.

```sql
SELECT "naksha~admin".naksha_partman_maintain(
    'naksha_custom_test', 'foo', 37, 16
);
SELECT * FROM "naksha~admin".naksha_partman;
SELECT * FROM pg_partition_tree('naksha_custom_test.foo');
```

The four arguments are schema, root table, shift and performance partition count.
The registry records those four settings. First invocation creates the minimal
root (`fn`, `version`, `nv`), mandatory keys, HEAD-only default and finite history
ranges. Every nv child, including HEAD, has the requested fn range leaves.

History width is `1::bigint << shift`. The current range is derived from the UTC
date using Naksha's year/month/day version bits, not Unix time. Coverage is the
current range plus three future ranges. Existing older ranges are retained and
their missing leaves are repaired too. The full signed bigint fn domain is split
by `floor(i * 256 / partitions)` routing-byte boundaries using numeric arithmetic;
MINVALUE/MAXVALUE handle endpoints without bigint overflow.

The date-based prototype deliberately accepts shifts 32..50, not tiny test
intervals: 32 covers the entire daily sequence field. It rejects insufficient
remaining version space for all three future ranges. Partition counts are 1..256.
Root names are limited to 36 bytes to reserve child-name suffix space. Target
schemas must exist; quoted schema/table names are supported.

Repeated calls preserve data and create only missing ranges/leaves. Per-target
transaction-scoped advisory locks serialize cooperating maintenance invocations.
Settings are create-only. Unregistered existing roots and conflicting partition
bounds/keys are rejected rather than adopted, dropped or silently altered.

Verified on PostgreSQL 18.6: 16-way HEAD/history distribution including bigint
extremes, current plus three future ranges, repeat calls, repair of an empty
missing leaf and an empty missing history range, rejection of changed settings,
and a quoted-schema 3-way layout. The committed foo example retains 34 rows and
80 leaves (five nv children with 16 fn leaves each). Auxiliary checks rolled back.

This is not yet a production replacement: no registry-scanning wrapper, pg_cron
schedule, retention, payload column configuration, manual-version/import coverage,
or index/constraint reconciliation is implemented. No concurrent-session stress
test has run. The caller owns commit/rollback; DDL can wait for application locks.
An hourly cron wrapper can iterate the registry and invoke this function, but its
failure isolation and monitoring still need implementation. The prototype adds no
cron job and does not change the existing production Kotlin/SQL paths.

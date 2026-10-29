\set ON_ERROR_STOP on
-- One inspectable case. Does not modify the earlier spike or application schemas.
-- psql -X -f ai/NAKSHA-33-foo-16.sql
-- Retains the result for inspection; refuses to overwrite an existing schema.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

CREATE SCHEMA partman;
CREATE EXTENSION pg_partman SCHEMA partman;
CREATE SCHEMA naksha_partman_16;
CREATE TABLE naksha_partman_16.foo (
    fn bigint NOT NULL,
    version bigint NOT NULL,
    nv bigint NOT NULL,
    CHECK (nv >= 0 AND nv <= 9007199254740991),
    PRIMARY KEY (nv, fn, version)
) PARTITION BY RANGE (nv);
CREATE UNIQUE INDEX ON naksha_partman_16.foo (fn, nv) INCLUDE (version);

CREATE TABLE naksha_partman_16.foo_p0
    PARTITION OF naksha_partman_16.foo
    FOR VALUES FROM (0) TO (100) PARTITION BY RANGE (fn);
CREATE TABLE naksha_partman_16.foo_head
    PARTITION OF naksha_partman_16.foo DEFAULT PARTITION BY RANGE (fn);
ALTER TABLE naksha_partman_16.foo_head ADD CHECK (nv = 9007199254740991);

-- int8 has 2^64 distinct values. 2^64 / 16 = 2^60 = 1152921504606846976.
-- PostgreSQL's final exclusive bound must be MAXVALUE, not bigint max.
-- These 16 leaves per nv child are explicitly created by this script, not partman.
DO $leaves$
DECLARE
    parent_name text;
    slot integer;
    lower_bound text;
    upper_bound text;
BEGIN
    FOREACH parent_name IN ARRAY ARRAY['foo_p0', 'foo_head'] LOOP
        FOR slot IN 0..15 LOOP
            lower_bound := CASE WHEN slot = 0 THEN 'MINVALUE'
                ELSE (-9223372036854775808::numeric + slot * 1152921504606846976::numeric)::text END;
            upper_bound := CASE WHEN slot = 15 THEN 'MAXVALUE'
                ELSE (-9223372036854775808::numeric + (slot + 1) * 1152921504606846976::numeric)::text END;
            EXECUTE format('CREATE TABLE naksha_partman_16.%I
                PARTITION OF naksha_partman_16.%I FOR VALUES FROM (%s) TO (%s)',
                parent_name || '_fn_' || slot, parent_name, lower_bound, upper_bound);
        END LOOP;
    END LOOP;
END
$leaves$;

-- One feature at each range's first value, plus bigint max to test the last endpoint.
INSERT INTO naksha_partman_16.foo (fn, version, nv)
SELECT (-9223372036854775808::numeric + slot * 1152921504606846976::numeric)::bigint,
       4, nv
FROM generate_series(0, 15) slot CROSS JOIN (VALUES (50::bigint), (9007199254740991::bigint)) states(nv);
INSERT INTO naksha_partman_16.foo VALUES
    (9223372036854775807, 4, 50), (9223372036854775807, 4, 9007199254740991);

DO $verify$
DECLARE
    parent_name text;
    slot integer;
    actual_count integer;
BEGIN
    FOREACH parent_name IN ARRAY ARRAY['foo_p0', 'foo_head'] LOOP
        IF (SELECT count(*) FROM pg_inherits
            WHERE inhparent = format('naksha_partman_16.%I', parent_name)::regclass) <> 16 THEN
            RAISE EXCEPTION 'Expected exactly 16 leaves beneath %', parent_name;
        END IF;
        FOR slot IN 0..15 LOOP
            EXECUTE format('SELECT count(*) FROM naksha_partman_16.%I', parent_name || '_fn_' || slot)
                INTO actual_count;
            IF actual_count <> (CASE WHEN slot = 15 THEN 2 ELSE 1 END) THEN
                RAISE EXCEPTION 'Wrong distribution in % slot %: % rows', parent_name, slot, actual_count;
            END IF;
        END LOOP;
    END LOOP;
    BEGIN
        INSERT INTO naksha_partman_16.foo VALUES (1, 4, 100);
        RAISE EXCEPTION 'Uncovered finite nv incorrectly accepted by HEAD default';
    EXCEPTION WHEN check_violation THEN
        RAISE NOTICE 'PASS: uncovered finite nv rejected by HEAD-only default';
    END;
    RAISE NOTICE 'PASS: all 16 history and 16 HEAD leaves receive their expected features';
END
$verify$;

-- Register only the outer nv key. Keep registration outside the subpartition probe
-- so a rejected subpartition setup does not undo the maintenance configuration.
CREATE TABLE naksha_partman_16.partman_result (step text, accepted boolean, detail text);
DO $partman$
DECLARE
    ext text;
    ok boolean;
BEGIN
    SELECT n.nspname INTO STRICT ext FROM pg_extension e
    JOIN pg_namespace n ON n.oid = e.extnamespace WHERE e.extname = 'pg_partman';
    EXECUTE format('SELECT %I.create_parent(
        p_parent_table := ''naksha_partman_16.foo'', p_control := ''nv'',
        p_interval := ''100'', p_start_partition := ''0'', p_premake := 1,
        p_default_table := false, p_jobmon := false)', ext) INTO ok;
    INSERT INTO naksha_partman_16.partman_result VALUES ('register nv root', ok, NULL);
    IF ok IS DISTINCT FROM true THEN RAISE EXCEPTION 'Root registration failed'; END IF;
    BEGIN
        EXECUTE format('SELECT %I.create_sub_parent(
            p_top_parent := ''naksha_partman_16.foo'', p_control := ''fn'',
            p_interval := ''1152921504606846976'', p_start_partition := ''-9223372036854775808'',
            p_premake := 15, p_default_table := false,
            p_declarative_check := ''yes'', p_jobmon := false)', ext) INTO ok;
        -- Roll back probe side effects even if a future version accepts it.
        RAISE EXCEPTION USING ERRCODE = 'ZX001', MESSAGE = 'create_sub_parent returned ' || ok::text;
    EXCEPTION WHEN OTHERS THEN
        INSERT INTO naksha_partman_16.partman_result
            VALUES ('fn interval 2^60, premake 15', SQLSTATE = 'ZX001', SQLERRM);
        RAISE NOTICE 'pg_partman probe: %', SQLERRM;
    END;
END
$partman$;

-- Registration premakes nv [100,200). Increasing premake must create further
-- history ranges while preserving the explicit fn leaves on p0 and HEAD.
UPDATE partman.part_config SET premake = 3
WHERE parent_table = 'naksha_partman_16.foo';
SELECT partman.run_maintenance(p_parent_table := 'naksha_partman_16.foo', p_jobmon := false);

DO $maintenance$
DECLARE
    child record;
    new_children integer := 0;
BEGIN
    FOR child IN
        SELECT c.oid, c.relkind, pg_get_expr(c.relpartbound, c.oid) AS bounds
        FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid
        WHERE i.inhparent = 'naksha_partman_16.foo'::regclass
    LOOP
        IF child.oid IN ('naksha_partman_16.foo_p0'::regclass, 'naksha_partman_16.foo_head'::regclass) THEN
            IF child.relkind <> 'p' OR
                (SELECT count(*) FROM pg_inherits WHERE inhparent = child.oid) <> 16 THEN
                RAISE EXCEPTION 'Existing 16-leaf layout changed: %', child.oid::regclass;
            END IF;
        ELSE
            new_children := new_children + 1;
            INSERT INTO naksha_partman_16.partman_result VALUES (
                'maintenance child ' || child.oid::regclass::text,
                child.relkind = 'p',
                child.bounds || '; key=' || coalesce(pg_get_partkeydef(child.oid), 'NONE (plain table)')
            );
        END IF;
    END LOOP;
    IF new_children < 3 THEN RAISE EXCEPTION 'Expected premade history ranges'; END IF;
    IF (SELECT count(*) FROM naksha_partman_16.foo) <> 34 THEN
        RAISE EXCEPTION 'Maintenance changed existing test data';
    END IF;
    RAISE NOTICE 'PASS: nv maintenance creates ranges and preserves existing 16-leaf children';
END
$maintenance$;

-- These features have widely separated fn values but all route to the same
-- plain, partman-created nv child if no fn subpartition provisioning is configured.
INSERT INTO naksha_partman_16.foo VALUES
    (-9223372036854775808, 4, 250), (0, 4, 250), (9223372036854775807, 4, 250);

SELECT t.relid::regclass, t.isleaf, pg_get_partkeydef(t.relid) AS partition_key,
       pg_get_expr(c.relpartbound, c.oid) AS bounds
FROM pg_partition_tree('naksha_partman_16.foo') t JOIN pg_class c ON c.oid = t.relid;
SELECT tableoid::regclass AS physical_leaf, count(*) AS features, min(fn), max(fn), min(nv) AS nv
FROM naksha_partman_16.foo GROUP BY tableoid ORDER BY tableoid::regclass::text;
SELECT * FROM naksha_partman_16.partman_result;
COMMIT;

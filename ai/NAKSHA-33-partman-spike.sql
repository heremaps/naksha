\set ON_ERROR_STOP on
-- Run against a disposable test database with pg_partman available:
-- psql -X -f ai/NAKSHA-33-partman-spike.sql
-- Default: roll back all objects/configuration. To retain them for inspection:
-- psql -X -v keep_spike=true -f ai/NAKSHA-33-partman-spike.sql
\if :{?keep_spike}
\else
\set keep_spike false
\endif
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

-- Install only if absent, in a new schema rather than reusing application objects.
DO $install$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_partman') THEN
        CREATE SCHEMA naksha_partman_spike_extension;
        CREATE EXTENSION pg_partman SCHEMA naksha_partman_spike_extension;
    END IF;
END
$install$;

SELECT version();
SELECT e.extversion, n.nspname AS extension_schema
FROM pg_extension e JOIN pg_namespace n ON n.oid = e.extnamespace
WHERE e.extname = 'pg_partman';

SELECT p.proname, pg_get_function_arguments(p.oid)
FROM pg_proc p JOIN pg_extension e ON e.extnamespace = p.pronamespace
WHERE e.extname = 'pg_partman'
AND p.proname IN ('create_parent', 'create_sub_parent', 'run_maintenance');

-- Refuse to reuse or remove a schema from an earlier/manual experiment.
CREATE SCHEMA naksha_partman_spike;

DO $spike$
DECLARE
    extension_schema text;
    root_name text;
    root_id regclass;
    head_id regclass;
    precreated_head_id regclass;
    children_before integer;
    children_after integer;
    accepted boolean;
    actual_leaf regclass;
    boundary_fn bigint;
    groups integer;
BEGIN
    SELECT n.nspname INTO STRICT extension_schema
    FROM pg_extension e JOIN pg_namespace n ON n.oid = e.extnamespace
    WHERE e.extname = 'pg_partman';

    FOR groups IN 1..2 LOOP
        root_name := 'rows_' || groups;
        EXECUTE format('CREATE TABLE naksha_partman_spike.%I (
            fn bigint NOT NULL, version bigint NOT NULL, nv bigint NOT NULL,
            CHECK (nv >= 0 AND nv <= 9007199254740991),
            PRIMARY KEY (nv, fn, version)
        ) PARTITION BY RANGE (nv)', root_name);
        root_id := format('naksha_partman_spike.%I', root_name)::regclass;
        EXECUTE format('CREATE UNIQUE INDEX ON %s (fn, nv) INCLUDE (version)', root_id);

        IF groups = 2 THEN
            -- Probe whether create_parent reuses a pre-created partitioned default.
            -- This name is part of the experiment, not the library naming policy.
            EXECUTE format('CREATE TABLE naksha_partman_spike.%I
                (LIKE %s INCLUDING CONSTRAINTS) PARTITION BY RANGE (fn)',
                root_name || '_default', root_id);
            precreated_head_id := format('naksha_partman_spike.%I', root_name || '_default')::regclass;
            EXECUTE format('CREATE TABLE naksha_partman_spike.head_negative
                PARTITION OF naksha_partman_spike.%I
                FOR VALUES FROM (MINVALUE) TO (0)', root_name || '_default');
            EXECUTE format('CREATE TABLE naksha_partman_spike.head_positive
                PARTITION OF naksha_partman_spike.%I
                FOR VALUES FROM (0) TO (MAXVALUE)', root_name || '_default');
        END IF;

        BEGIN
            EXECUTE format('SELECT %I.create_parent(
                p_parent_table := $1, p_control := ''nv'', p_interval := ''100'',
                p_premake := 3, p_start_partition := ''0'',
                p_default_table := true, p_jobmon := false)', extension_schema)
            INTO accepted USING root_id::text;
        EXCEPTION WHEN OTHERS THEN
            -- 5.0.1 wraps duplicate_table in a generic exception. Do not mask other failures.
            IF groups <> 2 OR position('relation "rows_2_default" already exists' IN SQLERRM) = 0 THEN
                RAISE;
            END IF;
            RAISE NOTICE 'Pre-created default reuse unsupported; probing explicit attachment';
            EXECUTE format('SELECT %I.create_parent(
                p_parent_table := $1, p_control := ''nv'', p_interval := ''100'',
                p_premake := 3, p_start_partition := ''0'',
                p_default_table := false, p_jobmon := false)', extension_schema)
            INTO accepted USING root_id::text;
            EXECUTE format('ALTER TABLE %s ATTACH PARTITION %s DEFAULT', root_id, precreated_head_id);
        END;
        IF accepted IS DISTINCT FROM true THEN
            RAISE EXCEPTION 'create_parent did not succeed for %', root_id;
        END IF;

        SELECT c.oid::regclass INTO STRICT head_id
        FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid
        WHERE i.inhparent = root_id
        AND pg_get_expr(c.relpartbound, c.oid) = 'DEFAULT';
        EXECUTE format('ALTER TABLE %s ADD CHECK (nv = 9007199254740991)', head_id);
        IF groups = 2 AND head_id <> precreated_head_id THEN
            RAISE EXCEPTION 'create_parent did not reuse the pre-created default';
        END IF;
        IF groups = 2 AND NOT EXISTS (
            SELECT 1 FROM pg_partitioned_table WHERE partrelid = head_id
        ) THEN
            RAISE EXCEPTION 'Pre-created default is not partitioned: %', head_id;
        END IF;
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = head_id AND contype = 'c' AND convalidated
            AND pg_get_constraintdef(oid) LIKE '%nv =%9007199254740991%'
        ) THEN
            RAISE EXCEPTION 'HEAD-only check is absent or not validated for %', root_id;
        END IF;

        -- Physical fn bounds must include both bigint extremes, not just ordinary IDs.
        FOREACH boundary_fn IN ARRAY ARRAY[
            '-9223372036854775808'::bigint, -1::bigint, 0::bigint,
            '9223372036854775807'::bigint
        ] LOOP
            EXECUTE format('INSERT INTO %s VALUES ($1, 80, 9007199254740991)
                RETURNING tableoid::regclass', root_id)
                INTO actual_leaf USING boundary_fn;
            IF groups = 1 AND actual_leaf <> head_id THEN
                RAISE EXCEPTION 'HEAD row routed outside default for %', root_id;
            ELSIF groups = 2 AND actual_leaf <> (CASE WHEN boundary_fn < 0
                THEN to_regclass('naksha_partman_spike.head_negative')
                ELSE to_regclass('naksha_partman_spike.head_positive') END) THEN
                RAISE EXCEPTION 'Incorrect fn routing for %: %', boundary_fn, actual_leaf;
            END IF;
            BEGIN
                EXECUTE format('INSERT INTO %s VALUES ($1, 84, 9007199254740991)', root_id)
                    USING boundary_fn;
                RAISE EXCEPTION 'Duplicate HEAD feature was accepted';
            EXCEPTION WHEN unique_violation THEN
                NULL;
            END;
            EXECUTE format('UPDATE %s SET nv = 100 WHERE fn = $1
                RETURNING tableoid::regclass', root_id)
                INTO actual_leaf USING boundary_fn;
            IF actual_leaf = head_id OR EXISTS (
                SELECT 1 FROM pg_inherits WHERE inhparent = head_id AND inhrelid = actual_leaf
            ) THEN
                RAISE EXCEPTION 'Archived boundary fn stayed in HEAD: %', boundary_fn;
            END IF;
            EXECUTE format('DELETE FROM %s WHERE fn = $1', root_id) USING boundary_fn;
        END LOOP;
        BEGIN
            EXECUTE format('INSERT INTO %s VALUES (3, 80, NULL)', root_id);
            RAISE EXCEPTION 'NULL nv was accepted';
        EXCEPTION WHEN not_null_violation THEN
            RAISE NOTICE 'PASS: NULL nv rejected for %', root_id;
        END;
        RAISE NOTICE 'PASS: bigint boundary routing and HEAD uniqueness for %', root_id;

        EXECUTE format('INSERT INTO %s VALUES
            (-1, 100, 9007199254740991), (1, 100, 9007199254740991),
            (2, 96, 100)', root_id);
        -- Move one HEAD row into finite history through the root.
        EXECUTE format('UPDATE %s SET nv = 200 WHERE fn = -1', root_id);
        EXECUTE format('SELECT count(*) = 1 FROM %s
            WHERE fn = -1 AND version = 100 AND nv = 200', root_id) INTO accepted;
        IF accepted IS DISTINCT FROM true THEN
            RAISE EXCEPTION 'Archival did not preserve the old state for %', root_id;
        END IF;

        BEGIN
            EXECUTE format('INSERT INTO %s VALUES (3, 100, 800000)', root_id);
            RAISE EXCEPTION 'Uncovered finite nv was accepted';
        EXCEPTION WHEN check_violation THEN
            RAISE NOTICE 'PASS: uncovered finite nv rejected for %', root_id;
        END;
        BEGIN
            EXECUTE format('INSERT INTO %s VALUES (3, 100, 9007199254740992)', root_id);
            RAISE EXCEPTION 'nv above HEAD was accepted';
        EXCEPTION WHEN check_violation THEN
            RAISE NOTICE 'PASS: nv above HEAD rejected for %', root_id;
        END;

        SELECT count(*) INTO children_before FROM pg_inherits WHERE inhparent = root_id;
        EXECUTE format('UPDATE %I.part_config SET premake = 6 WHERE parent_table = $1',
            extension_schema) USING root_id::text;
        EXECUTE format('SELECT %I.run_maintenance(p_parent_table := $1)', extension_schema)
            USING root_id::text;
        SELECT count(*) INTO children_after FROM pg_inherits WHERE inhparent = root_id;
        IF children_after <= children_before THEN
            RAISE EXCEPTION 'Maintenance did not premake more finite children for %', root_id;
        END IF;
        EXECUTE format('SELECT count(*) = 3
            AND count(*) FILTER (WHERE fn = 1 AND version = 100 AND nv = 9007199254740991) = 1
            AND count(*) FILTER (WHERE fn = -1 AND version = 100 AND nv = 200) = 1
            AND count(*) FILTER (WHERE fn = 2 AND version = 96 AND nv = 100) = 1
            FROM %s', root_id)
            INTO accepted;
        IF accepted IS DISTINCT FROM true THEN
            RAISE EXCEPTION 'Maintenance changed HEAD/history data for %', root_id;
        END IF;
        RAISE NOTICE 'PASS: % topology, archival routing, HEAD checks and maintenance', root_id;
    END LOOP;
END
$spike$;

SELECT t.relid::regclass, t.parentrelid::regclass, t.isleaf,
       pg_get_expr(c.relpartbound, c.oid) AS bounds
FROM pg_partition_tree('naksha_partman_spike.rows_1') t
JOIN pg_class c ON c.oid = t.relid;
SELECT t.relid::regclass, t.parentrelid::regclass, t.isleaf,
       pg_get_expr(c.relpartbound, c.oid) AS bounds
FROM pg_partition_tree('naksha_partman_spike.rows_2') t
JOIN pg_class c ON c.oid = t.relid;

-- Empty, independent root: create_sub_parent may drop/recreate existing children.
CREATE TABLE naksha_partman_spike.sub_root (
    fn bigint NOT NULL, version bigint NOT NULL, nv bigint NOT NULL,
    PRIMARY KEY (nv, fn, version)
) PARTITION BY RANGE (nv);
CREATE UNIQUE INDEX ON naksha_partman_spike.sub_root (fn, nv) INCLUDE (version);
CREATE INDEX sub_custom ON naksha_partman_spike.sub_root (version) INCLUDE (fn, nv);

DO $subpart$
DECLARE
    ext text;
    ok boolean;
    child record;
    before_count integer;
    after_count integer;
    leaf regclass;
BEGIN
    SELECT n.nspname INTO STRICT ext FROM pg_extension e
    JOIN pg_namespace n ON n.oid = e.extnamespace WHERE e.extname = 'pg_partman';
    EXECUTE format('SELECT %I.create_parent(
        p_parent_table := ''naksha_partman_spike.sub_root'', p_control := ''nv'',
        p_interval := ''100'', p_start_partition := ''0'', p_premake := 1,
        p_jobmon := false)', ext) INTO ok;
    IF ok IS DISTINCT FROM true THEN RAISE EXCEPTION 'Sub-root setup failed'; END IF;
    BEGIN
        EXECUTE format('SELECT %I.create_sub_parent(
            p_top_parent := ''naksha_partman_spike.sub_root'', p_control := ''fn'',
            p_interval := ''100'', p_declarative_check := ''yes'', p_jobmon := false)', ext);
        RAISE EXCEPTION 'Expected equal parent/subpartition interval to be rejected';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%%Sub-partition interval cannot be greater than or equal%%' THEN RAISE; END IF;
        RAISE NOTICE 'LIMITATION: fn interval must be smaller than nv interval';
    END;
    EXECUTE format('SELECT %I.create_sub_parent(
        p_top_parent := ''naksha_partman_spike.sub_root'', p_control := ''fn'',
        p_interval := ''10'', p_start_partition := ''0'', p_premake := 1,
        p_declarative_check := ''yes'', p_default_table := true, p_jobmon := false)', ext)
        INTO ok;
    IF ok IS DISTINCT FROM true THEN RAISE EXCEPTION 'Subpartition setup failed'; END IF;

    INSERT INTO naksha_partman_spike.sub_root VALUES (1, 80, 100);
    SELECT count(*) INTO before_count FROM pg_inherits
    WHERE inhparent = 'naksha_partman_spike.sub_root'::regclass;
    EXECUTE format('UPDATE %I.part_config SET premake = 3
        WHERE parent_table = ''naksha_partman_spike.sub_root''', ext);
    EXECUTE format('SELECT %I.run_maintenance(
        p_parent_table := ''naksha_partman_spike.sub_root'', p_jobmon := false)', ext);
    SELECT count(*) INTO after_count FROM pg_inherits
    WHERE inhparent = 'naksha_partman_spike.sub_root'::regclass;
    IF after_count <= before_count THEN RAISE EXCEPTION 'No future finite children created'; END IF;

    FOR child IN
        SELECT c.oid, c.relkind, pg_get_expr(c.relpartbound, c.oid) AS bounds
        FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid
        WHERE i.inhparent = 'naksha_partman_spike.sub_root'::regclass
    LOOP
        IF child.bounds = 'DEFAULT' THEN
            IF child.relkind <> 'r' THEN RAISE EXCEPTION 'Unexpected default conversion'; END IF;
        ELSIF child.relkind <> 'p' OR pg_get_partkeydef(child.oid) <> 'RANGE (fn)' THEN
            RAISE EXCEPTION 'Finite child is not fn-partitioned: %', child.oid::regclass;
        END IF;
    END LOOP;

    -- Check inherited mandatory and custom indexes on every existing/future leaf.
    FOR child IN SELECT relid FROM pg_partition_tree('naksha_partman_spike.sub_root') WHERE isleaf LOOP
        IF NOT EXISTS (SELECT 1 FROM pg_index i WHERE i.indrelid = child.relid
            AND i.indisvalid AND i.indisunique
            AND pg_get_indexdef(i.indexrelid) LIKE '%(fn, nv) INCLUDE (version)%')
        OR NOT EXISTS (SELECT 1 FROM pg_index i WHERE i.indrelid = child.relid
            AND i.indisvalid
            AND pg_get_indexdef(i.indexrelid) LIKE '%(version) INCLUDE (fn, nv)%') THEN
            RAISE EXCEPTION 'Missing mandatory/custom leaf index: %', child.relid;
        END IF;
    END LOOP;

    INSERT INTO naksha_partman_spike.sub_root VALUES (2, 80, 100)
        RETURNING tableoid::regclass INTO leaf;
    IF pg_get_expr((SELECT relpartbound FROM pg_class WHERE oid = leaf), leaf) = 'DEFAULT' THEN
        RAISE NOTICE 'LIMITATION: fn=2 at nv=100 routes to sub-default (%)', leaf;
    END IF;
    INSERT INTO naksha_partman_spike.sub_root VALUES (9223372036854775807, 80, 100)
        RETURNING tableoid::regclass INTO leaf;
    IF pg_get_expr((SELECT relpartbound FROM pg_class WHERE oid = leaf), leaf) <> 'DEFAULT' THEN
        RAISE EXCEPTION 'Expected bigint maximum to require sub-default';
    END IF;
    RAISE NOTICE 'PASS: existing/future finite fn subparents and indexes; LIMITATION: bigint max uses DEFAULT';

    -- Nested integer bounds may skip an out-of-range request before arithmetic.
    EXECUTE format('SELECT %I.create_partition_id(
            p_parent_table := ''naksha_partman_spike.sub_root_p100'',
            p_partition_ids := ARRAY[9223372036854775800::bigint])', ext) INTO ok;
    IF ok IS DISTINCT FROM false THEN
        RAISE EXCEPTION 'Expected out-of-range nested partition request to be skipped';
    END IF;
    RAISE NOTICE 'LIMITATION: nested bigint-end partition request skipped, not provisioned';
END
$subpart$;

SELECT t.relid::regclass, t.isleaf, pg_get_expr(c.relpartbound, c.oid) AS bounds
FROM pg_partition_tree('naksha_partman_spike.sub_root') t
JOIN pg_class c ON c.oid = t.relid;

-- Retention and DROP cleanup still need db-02-d checks.
\if :keep_spike
COMMIT;
\else
ROLLBACK;
\endif

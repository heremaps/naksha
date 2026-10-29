\set ON_ERROR_STOP on
-- Prototype installation. No pg_partman dependency, cron job, or history deletion.
BEGIN;
CREATE SCHEMA IF NOT EXISTS "naksha~admin";
CREATE TABLE IF NOT EXISTS "naksha~admin".naksha_partman (
    schema_name text NOT NULL,
    table_name text NOT NULL,
    shift integer NOT NULL CHECK (shift BETWEEN 32 AND 50),
    partitions integer NOT NULL CHECK (partitions BETWEEN 1 AND 256),
    PRIMARY KEY (schema_name, table_name)
);

CREATE OR REPLACE FUNCTION "naksha~admin".naksha_partman_maintain(
    p_schema text, p_table text, p_shift integer, p_partitions integer
) RETURNS void
LANGUAGE plpgsql
SET search_path = pg_catalog, pg_temp
AS $maintain$
DECLARE
    head constant bigint := 9007199254740991;
    root regclass;
    child regclass;
    existing record;
    registered record;
    today date := (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date;
    current_version bigint;
    width bigint;
    start_nv bigint;
    end_nv bigint;
    range_no integer;
    child_name text;
    leaf_name text;
    slot integer;
    low_fn text;
    high_fn text;
    expected_bound text;
BEGIN
    IF p_schema IS NULL OR p_schema = '' OR p_table IS NULL OR p_table = ''
       OR octet_length(p_schema) > 63 OR octet_length(p_table) > 63
       OR p_shift IS NULL OR p_shift NOT BETWEEN 32 AND 50
       OR p_partitions IS NULL OR p_partitions NOT BETWEEN 1 AND 256 THEN
        RAISE EXCEPTION 'Invalid schema/table, shift (32..50), or partitions (1..256)'
            USING ERRCODE = '22023';
    END IF;
    -- Reserve suffix room instead of allowing PostgreSQL to truncate child names.
    IF octet_length(p_table) > 36 THEN
        RAISE EXCEPTION 'Prototype root name must fit in 36 bytes' USING ERRCODE = '22023';
    END IF;
    IF to_regnamespace(format('%I', p_schema)) IS NULL THEN
        RAISE EXCEPTION 'Schema % does not exist', p_schema;
    END IF;

    -- Serialize concurrent maintenance for this target until caller commit/rollback.
    PERFORM pg_advisory_xact_lock(hashtextextended(format('%I.%I', p_schema, p_table), 0));
    SELECT * INTO registered FROM "naksha~admin".naksha_partman
        WHERE schema_name = p_schema AND table_name = p_table;
    IF FOUND AND (registered.shift <> p_shift OR registered.partitions <> p_partitions) THEN
        RAISE EXCEPTION 'Partition settings are create-only for %.%', p_schema, p_table;
    END IF;

    root := to_regclass(format('%I.%I', p_schema, p_table));
    IF root IS NOT NULL AND registered.schema_name IS NULL THEN
        RAISE EXCEPTION 'Refusing to adopt unregistered existing table %.%', p_schema, p_table;
    END IF;
    IF root IS NULL THEN
        EXECUTE format('CREATE TABLE %I.%I (
            fn bigint NOT NULL, version bigint NOT NULL, nv bigint NOT NULL,
            CHECK (nv >= 0 AND nv <= 9007199254740991),
            PRIMARY KEY (nv, fn, version)
        ) PARTITION BY RANGE (nv)', p_schema, p_table);
        EXECUTE format('CREATE UNIQUE INDEX ON %I.%I (fn, nv) INCLUDE (version)', p_schema, p_table);
        root := to_regclass(format('%I.%I', p_schema, p_table));
    END IF;
    IF pg_get_partkeydef(root) IS DISTINCT FROM 'RANGE (nv)' THEN
        RAISE EXCEPTION 'Root % is not partitioned by nv', root;
    END IF;
    INSERT INTO "naksha~admin".naksha_partman VALUES (p_schema, p_table, p_shift, p_partitions)
        ON CONFLICT (schema_name, table_name) DO NOTHING;

    width := 1::bigint << p_shift;
    current_version := (extract(year FROM today)::bigint << 41)
        | (extract(month FROM today)::bigint << 37)
        | (extract(day FROM today)::bigint << 32);
    IF current_version < 0 OR current_version >= head THEN
        RAISE EXCEPTION 'Current date is outside the supported dated-version domain';
    END IF;
    IF (current_version / width) * width + 4 * width > head THEN
        RAISE EXCEPTION 'Insufficient version space for current plus three future history ranges';
    END IF;

    -- HEAD plus current finite range and exactly three future ranges.
    FOR range_no IN -1..3 LOOP
        IF range_no = -1 THEN
            child_name := p_table || '_head';
            expected_bound := 'DEFAULT';
        ELSE
            start_nv := (current_version / width) * width + range_no * width;
            IF start_nv >= head THEN CONTINUE; END IF;
            end_nv := least(start_nv + width, head);
            child_name := p_table || '_h' || start_nv;
            expected_bound := format('FOR VALUES FROM (''%s'') TO (''%s'')', start_nv, end_nv);
        END IF;
        child := to_regclass(format('%I.%I', p_schema, child_name));
        IF child IS NULL THEN
            IF range_no = -1 THEN
                EXECUTE format('CREATE TABLE %I.%I PARTITION OF %s DEFAULT PARTITION BY RANGE (fn)',
                    p_schema, child_name, root);
                EXECUTE format('ALTER TABLE %I.%I ADD CHECK (nv = %s)', p_schema, child_name, head);
            ELSE
                EXECUTE format('CREATE TABLE %I.%I PARTITION OF %s
                    FOR VALUES FROM (%s) TO (%s) PARTITION BY RANGE (fn)',
                    p_schema, child_name, root, start_nv, end_nv);
            END IF;
            child := to_regclass(format('%I.%I', p_schema, child_name));
        END IF;
        SELECT i.inhparent, pg_get_expr(c.relpartbound, c.oid) AS bounds
            INTO existing FROM pg_class c LEFT JOIN pg_inherits i ON i.inhrelid = c.oid
            WHERE c.oid = child;
        IF existing.inhparent IS DISTINCT FROM root::oid
           OR existing.bounds IS DISTINCT FROM expected_bound
           OR pg_get_partkeydef(child) IS DISTINCT FROM 'RANGE (fn)' THEN
            RAISE EXCEPTION 'Conflicting partition %', child;
        END IF;
        IF range_no = -1 AND NOT EXISTS (
            SELECT 1 FROM pg_constraint WHERE conrelid = child AND convalidated
                AND pg_get_constraintdef(oid) LIKE '%nv =%9007199254740991%'
        ) THEN
            RAISE EXCEPTION 'HEAD constraint missing on %', child;
        END IF;
    END LOOP;

    -- Repair leaves on all retained history children as well as HEAD, not only
    -- current/future ranges. Bounds split the 256 routing bytes without overflow.
    FOR existing IN
        SELECT c.oid, c.relname FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid
        WHERE i.inhparent = root
    LOOP
        IF pg_get_partkeydef(existing.oid) IS DISTINCT FROM 'RANGE (fn)' THEN
            RAISE EXCEPTION 'Unexpected non-fn-partitioned child %', existing.oid::regclass;
        END IF;
        FOR slot IN 0..p_partitions - 1 LOOP
            low_fn := CASE WHEN slot = 0 THEN 'MINVALUE' ELSE
                (-9223372036854775808::numeric
                + floor(slot * 256::numeric / p_partitions) * 72057594037927936::numeric)::text END;
            high_fn := CASE WHEN slot = p_partitions - 1 THEN 'MAXVALUE' ELSE
                (-9223372036854775808::numeric
                + floor((slot + 1) * 256::numeric / p_partitions) * 72057594037927936::numeric)::text END;
            leaf_name := existing.relname || '_f' || slot;
            child := to_regclass(format('%I.%I', p_schema, leaf_name));
            IF child IS NULL THEN
                EXECUTE format('CREATE TABLE %I.%I PARTITION OF %s FOR VALUES FROM (%s) TO (%s)',
                    p_schema, leaf_name, existing.oid::regclass, low_fn, high_fn);
            ELSE
                expected_bound := 'FOR VALUES FROM (' ||
                    CASE WHEN low_fn = 'MINVALUE' THEN low_fn ELSE quote_literal(low_fn) END ||
                    ') TO (' || CASE WHEN high_fn = 'MAXVALUE' THEN high_fn ELSE quote_literal(high_fn) END || ')';
                IF NOT EXISTS (SELECT 1 FROM pg_inherits WHERE inhparent = existing.oid AND inhrelid = child)
                    OR (SELECT pg_get_expr(relpartbound, oid) FROM pg_class WHERE oid = child)
                        IS DISTINCT FROM expected_bound THEN
                    RAISE EXCEPTION 'Conflicting performance leaf %', child;
                END IF;
            END IF;
        END LOOP;
    END LOOP;
END
$maintain$;
COMMIT;

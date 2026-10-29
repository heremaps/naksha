\set ON_ERROR_STOP on
\ir NAKSHA-33-custom-partman.sql
BEGIN;
SET LOCAL statement_timeout = '60s';
CREATE SCHEMA naksha_custom_test;
SELECT "naksha~admin".naksha_partman_maintain('naksha_custom_test', 'foo', 37, 16);
SELECT "naksha~admin".naksha_partman_maintain('naksha_custom_test', 'foo', 37, 16);

DO $test$
DECLARE
    root constant regclass := 'naksha_custom_test.foo';
    history regclass;
    first_nv bigint;
    slot integer;
    feature bigint;
    actual regclass;
    missing regclass;
    item record;
BEGIN
    IF (SELECT count(*) FROM pg_inherits WHERE inhparent = root) <> 5 THEN
        RAISE EXCEPTION 'Expected HEAD, current history, and three future ranges';
    END IF;
    SELECT c.oid::regclass INTO history FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid
        WHERE i.inhparent = root AND pg_get_expr(c.relpartbound, c.oid) <> 'DEFAULT'
        ORDER BY c.relname LIMIT 1;
    first_nv := substring(history::text FROM '_h([0-9]+)$')::bigint;
    FOR slot IN 0..15 LOOP
        feature := (-9223372036854775808::numeric + slot * 1152921504606846976::numeric)::bigint;
        INSERT INTO naksha_custom_test.foo VALUES (feature, 4, 9007199254740991)
            RETURNING tableoid::regclass INTO actual;
        IF actual <> to_regclass('naksha_custom_test.foo_head_f' || slot) THEN
            RAISE EXCEPTION 'Wrong HEAD routing for slot %', slot;
        END IF;
        INSERT INTO naksha_custom_test.foo VALUES (feature, 4, first_nv)
            RETURNING tableoid::regclass INTO actual;
        IF actual <> to_regclass(history::text || '_f' || slot) THEN
            RAISE EXCEPTION 'Wrong history routing for slot %', slot;
        END IF;
    END LOOP;
    INSERT INTO naksha_custom_test.foo VALUES
        (9223372036854775807, 4, first_nv), (9223372036854775807, 4, 9007199254740991);
    -- Delete an empty leaf in the furthest future range and verify repair.
    SELECT c.oid::regclass INTO missing FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid
        WHERE i.inhparent = root AND pg_get_expr(c.relpartbound, c.oid) <> 'DEFAULT'
        ORDER BY c.relname DESC LIMIT 1;
    EXECUTE format('DROP TABLE %s', to_regclass(missing::text || '_f7'));
    PERFORM "naksha~admin".naksha_partman_maintain('naksha_custom_test', 'foo', 37, 16);
    FOR item IN SELECT inhrelid FROM pg_inherits WHERE inhparent = root LOOP
        IF (SELECT count(*) FROM pg_inherits WHERE inhparent = item.inhrelid) <> 16 THEN
            RAISE EXCEPTION 'Expected 16 leaves on every nv child';
        END IF;
    END LOOP;
    IF (SELECT count(*) FROM naksha_custom_test.foo) <> 34 THEN
        RAISE EXCEPTION 'Repeat maintenance changed sample data';
    END IF;
    RAISE NOTICE 'PASS: current + 3 future, 16 full-range leaves, routing, repeat calls and repair';
END
$test$;

SELECT * FROM "naksha~admin".naksha_partman;
SELECT tableoid::regclass AS leaf, count(*) FROM naksha_custom_test.foo GROUP BY tableoid ORDER BY 1;
COMMIT;

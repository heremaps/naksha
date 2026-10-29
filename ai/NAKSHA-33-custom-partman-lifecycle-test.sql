\set ON_ERROR_STOP on
-- Requires the installed prototype. Leaves retained inspection examples untouched.
BEGIN;
SET LOCAL statement_timeout = '60s';
CREATE SCHEMA naksha_lifecycle_test;
SELECT "naksha~admin".naksha_partman_maintain('naksha_lifecycle_test', 'foo', 37, 16);

DO $test$
DECLARE
    finite_nv bigint;
BEGIN
    INSERT INTO naksha_lifecycle_test.foo VALUES (1, 4, 9007199254740991);
    PERFORM "naksha~admin".naksha_partman_maintain('naksha_lifecycle_test', 'foo', 37, 16);
    IF (SELECT count(*) FROM naksha_lifecycle_test.foo WHERE nv = 9007199254740991) <> 1 THEN
        RAISE EXCEPTION 'Maintenance changed HEAD data';
    END IF;
    SELECT substring(c.relname FROM '_h([0-9]+)$')::bigint INTO finite_nv
    FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid
    WHERE i.inhparent = 'naksha_lifecycle_test.foo'::regclass
        AND pg_get_expr(c.relpartbound, c.oid) <> 'DEFAULT'
    ORDER BY c.relname LIMIT 1;
    BEGIN
        INSERT INTO naksha_lifecycle_test.foo VALUES (2, 4, finite_nv - 1);
        RAISE EXCEPTION 'Uncovered finite nv was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;

    DROP TABLE naksha_lifecycle_test.foo CASCADE;
    IF NOT EXISTS (SELECT 1 FROM "naksha~admin".naksha_partman
        WHERE schema_name = 'naksha_lifecycle_test' AND table_name = 'foo') THEN
        RAISE EXCEPTION 'Expected prototype registry row to survive raw DROP';
    END IF;
    -- This is intentional prototype behavior: a retained row recreates a missing root.
    PERFORM "naksha~admin".naksha_partman_maintain('naksha_lifecycle_test', 'foo', 37, 16);
    IF (SELECT count(*) FROM pg_partition_tree('naksha_lifecycle_test.foo') WHERE isleaf) <> 80 THEN
        RAISE EXCEPTION 'Missing root was not fully recreated';
    END IF;
    DROP SCHEMA naksha_lifecycle_test CASCADE;
    BEGIN
        PERFORM "naksha~admin".naksha_partman_maintain('naksha_lifecycle_test', 'foo', 37, 16);
        RAISE EXCEPTION 'Missing schema was silently recreated';
    EXCEPTION WHEN raise_exception THEN
        IF SQLERRM <> 'Schema naksha_lifecycle_test does not exist' THEN RAISE; END IF;
    END;
    -- Production drop must unregister in the same transaction, before the next sweep.
    DELETE FROM "naksha~admin".naksha_partman WHERE schema_name = 'naksha_lifecycle_test';
    IF EXISTS (SELECT 1 FROM "naksha~admin".naksha_partman WHERE schema_name = 'naksha_lifecycle_test') THEN
        RAISE EXCEPTION 'Explicit registry cleanup failed';
    END IF;
    RAISE NOTICE 'PASS: HEAD preserved, uncovered nv rejected, missing root repaired, explicit cleanup verified';
END
$test$;
ROLLBACK;

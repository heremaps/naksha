\set ON_ERROR_STOP on
-- Run with psql -X -f ai/NAKSHA-33-production-partman-test.sql in a disposable
-- database supplied by the operator. Nothing is committed, including installation.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';
DO $test$
BEGIN
  IF to_regclass('"naksha~admin".naksha_partman') IS NOT NULL THEN
    RAISE EXCEPTION 'Refusing existing partition registry; use a disposable database';
  END IF;
END
$test$;
CREATE SCHEMA IF NOT EXISTS "naksha~admin";
\ir NAKSHA-33-plpgsql-core-reference.sql
CREATE SCHEMA "Partman Test";

CREATE FUNCTION pg_temp.check_true(p_ok boolean, p_message text) RETURNS void
LANGUAGE plpgsql AS $test$
BEGIN
  IF p_ok IS DISTINCT FROM true THEN RAISE EXCEPTION 'FAIL: %', p_message; END IF;
END
$test$;

CREATE FUNCTION pg_temp.expect_failure(p_sql text, p_state text DEFAULT NULL) RETURNS void
LANGUAGE plpgsql AS $test$
DECLARE
  failed boolean := false;
BEGIN
  -- The exception block is a subtransaction: also proves failed calls are atomic.
  BEGIN
    EXECUTE p_sql;
  EXCEPTION WHEN OTHERS THEN
    IF p_state IS NOT NULL AND SQLSTATE <> p_state THEN RAISE; END IF;
    failed := true;
  END;
  PERFORM pg_temp.check_true(failed, 'expected failure: ' || p_sql);
END
$test$;

CREATE FUNCTION pg_temp.make_root(p_name text) RETURNS oid
LANGUAGE plpgsql AS $test$
BEGIN
  EXECUTE format('CREATE TABLE "Partman Test".%I (
    fn bigint NOT NULL, version bigint NOT NULL, nv bigint NOT NULL,
    payload text NOT NULL DEFAULT ''payload'', extra integer DEFAULT 17,
    CONSTRAINT %I CHECK (extra >= 0), PRIMARY KEY (nv, fn, version)
  ) PARTITION BY RANGE (nv)', p_name, p_name || '$payload_check');
  EXECUTE format('CREATE UNIQUE INDEX %I ON "Partman Test".%I (fn, nv) INCLUDE (version)', p_name || '$unique', p_name);
  EXECUTE format('CREATE INDEX %I ON "Partman Test".%I (payload) INCLUDE (fn, nv, version)', p_name || '$custom', p_name);
  RETURN to_regclass(format('"Partman Test".%I', p_name));
END
$test$;

CREATE FUNCTION pg_temp.check_inheritance(p_name text) RETURNS void
LANGUAGE plpgsql AS $test$
DECLARE
  root oid := to_regclass(format('"Partman Test".%I', p_name));
  edge record;
  parent_index record;
BEGIN
  FOR edge IN
    SELECT t.relid::oid AS child, t.parentrelid::oid AS parent
    FROM pg_partition_tree(root) t WHERE t.parentrelid IS NOT NULL
  LOOP
    PERFORM pg_temp.check_true(EXISTS (
      SELECT 1 FROM pg_attribute WHERE attrelid = edge.child AND attname = 'payload'
        AND atttypid = 'text'::regtype AND attnotnull
    ), 'custom column inherited by ' || edge.child::regclass);
    PERFORM pg_temp.check_true(EXISTS (
      SELECT 1 FROM pg_constraint WHERE conrelid = edge.child AND contype = 'c'
        AND conname = p_name || '$payload_check' AND convalidated AND coninhcount > 0
    ), 'custom check inherited by ' || edge.child::regclass);
    FOR parent_index IN SELECT indexrelid FROM pg_index WHERE indrelid = edge.parent LOOP
      PERFORM pg_temp.check_true(EXISTS (
        SELECT 1 FROM pg_inherits h JOIN pg_index i ON i.indexrelid = h.inhrelid
        WHERE h.inhparent = parent_index.indexrelid AND i.indrelid = edge.child
          AND i.indisvalid AND i.indisready
      ), 'native mandatory/custom index inheritance on ' || edge.child::regclass);
    END LOOP;
  END LOOP;
END
$test$;

-- Small representative matrix, not a shift/count Cartesian product. The 256
-- case starts at the last finite range, so only 512 physical leaves are needed.
SELECT pg_temp.make_root('single');
SELECT pg_temp.make_root('three');
SELECT pg_temp.make_root('seven');
SELECT pg_temp.make_root('full');
SELECT "naksha~admin".naksha_partman_register('Partman Test', 'single', 12, 1, 2048, 61, 93, NULL);
SELECT "naksha~admin".naksha_partman_register('Partman Test', 'three', 41, 3, 2048, 62, 94, 'pg_default');
SELECT "naksha~admin".naksha_partman_register('Partman Test', 'seven', 12, 7, 2048, 63, 95, NULL);
SELECT "naksha~admin".naksha_partman_register('Partman Test', 'full', 41, 256, 2048, 64, 96, NULL);
-- Registration is idempotent only for the same root and complete configuration.
SELECT "naksha~admin".naksha_partman_register('Partman Test', 'seven', 12, 7, 2048, 63, 95, NULL);
SELECT "naksha~admin".naksha_partman_maintain('Partman Test', 'single', 9007199254740990);
SELECT "naksha~admin".naksha_partman_maintain('Partman Test', 'three', 0);
SELECT "naksha~admin".naksha_partman_maintain('Partman Test', 'seven', 0);
SELECT "naksha~admin".naksha_partman_maintain('Partman Test', 'full', 9007199254740990);

SELECT pg_temp.check_true((
  SELECT count(*) = 2 AND bool_and(isleaf) FROM pg_partition_tree('"Partman Test".single') WHERE level = 1
), 'count 1 uses direct nv leaves and clips future starts at HEAD');
SELECT pg_temp.check_true((
  SELECT count(*) = 5 FROM pg_partition_tree('"Partman Test".three') WHERE level = 1
), 'HEAD plus current and three future ranges');
SELECT pg_temp.check_true((
  SELECT count(*) = 512 FROM pg_partition_tree('"Partman Test".full') WHERE isleaf
), '256 slots on final finite and HEAD children');
SELECT pg_temp.check_true((
  SELECT pg_get_expr(relpartbound, oid) = 'FOR VALUES FROM (''9007199254736896'') TO (''9007199254740991'')'
  FROM pg_class WHERE oid = '"Partman Test"."single$2199023255551"'::regclass
), 'shift12 suffix is bigint and final finite upper bound excludes HEAD');
SELECT pg_temp.check_true((
  SELECT pg_get_expr(relpartbound, oid) = 'FOR VALUES FROM (''9005000231485440'') TO (''9007199254740991'')'
  FROM pg_class WHERE oid = '"Partman Test"."full$4095"'::regclass
), 'shift41 final finite range clipped at HEAD');
SELECT "naksha~admin".naksha_partman_maintain('Partman Test', 'single', 9007199254740990);

-- Every byte-block boundary and both bigint extremes route through the root.
-- This covers unequal floor(slot*256/count) groups for counts 3 and 7 too.
DO $test$
DECLARE
  name text;
  count_slots integer;
  byte_number integer;
  ordinal bigint;
  finite bigint;
  expected_slot integer;
  actual text;
  expected text;
BEGIN
  FOREACH name IN ARRAY ARRAY['single', 'three', 'seven', 'full'] LOOP
    count_slots := CASE name WHEN 'single' THEN 1 WHEN 'three' THEN 3 WHEN 'seven' THEN 7 ELSE 256 END;
    finite := CASE WHEN name IN ('single', 'full') THEN 9007199254740990 ELSE 0 END;
    FOR byte_number IN 0..255 LOOP
      ordinal := (-9223372036854775808::numeric + byte_number::numeric * 72057594037927936)::bigint;
      expected_slot := ((byte_number + 1) * count_slots - 1) / 256;
      expected := name || '$' || (finite >> CASE WHEN name IN ('three', 'full') THEN 41 ELSE 12 END)::text;
      IF count_slots > 1 THEN expected := expected || '$' || expected_slot; END IF;
      EXECUTE format('INSERT INTO "Partman Test".%I (fn, version, nv, payload)
        VALUES ($1, 0, $2, ''boundary'') RETURNING tableoid::regclass::text', name)
        INTO actual USING ordinal, finite;
      PERFORM pg_temp.check_true(actual = format('"Partman Test".%I', expected), 'finite routing ' || expected);
      EXECUTE format('INSERT INTO "Partman Test".%I (fn, version, nv)
        VALUES ($1, 0, 9007199254740991) RETURNING tableoid::regclass::text', name)
        INTO actual USING ordinal;
      expected := name || '$head';
      IF count_slots > 1 THEN expected := expected || '$' || expected_slot; END IF;
      PERFORM pg_temp.check_true(actual = format('"Partman Test".%I', expected), 'HEAD routing ' || expected);
    END LOOP;
    EXECUTE format('INSERT INTO "Partman Test".%I (fn, version, nv)
      VALUES (9223372036854775807, 0, $1), (9223372036854775807, 0, 9007199254740991)', name) USING finite;
    PERFORM pg_temp.check_inheritance(name);
  END LOOP;
END
$test$;

-- Mandatory unique key, inherited NOT NULL and custom checks, and DEFAULT never
-- accepting uncovered finite, negative, or out-of-domain nv values.
SELECT pg_temp.expect_failure('INSERT INTO "Partman Test".seven VALUES (-9223372036854775808, 1, 9007199254740991)', '23505');
SELECT pg_temp.expect_failure('INSERT INTO "Partman Test".seven (fn, version, nv, payload) VALUES (9, 0, 0, NULL)', '23502');
SELECT pg_temp.expect_failure('INSERT INTO "Partman Test".seven (fn, version, nv, extra) VALUES (9, 0, 0, -1)', '23514');
SELECT pg_temp.expect_failure('INSERT INTO "Partman Test".seven (fn, version, nv) VALUES (9, 0, 16384)', '23514');
SELECT pg_temp.expect_failure('INSERT INTO "Partman Test".seven (fn, version, nv) VALUES (9, 0, -1)', '23514');
SELECT pg_temp.expect_failure('INSERT INTO "Partman Test".seven (fn, version, nv) VALUES (9, 0, 9007199254740992)', '23514');
SELECT pg_temp.expect_failure('INSERT INTO "Partman Test".seven (fn, version, nv) VALUES (9, 0, NULL)', '23502');

-- An explicit imported version provisions its own aligned range, independently
-- of wall-clock time or a version sequence. Older missing leaves are repaired too.
CREATE INDEX "seven$late" ON "Partman Test".seven (extra) INCLUDE (nv, fn, version);
SELECT pg_temp.check_inheritance('seven');
DROP TABLE "Partman Test"."seven$0$1";
SELECT "naksha~admin".naksha_partman_maintain('Partman Test', 'seven', 40960000000000);
SELECT pg_temp.check_true(to_regclass('"Partman Test"."seven$10000000000$6"') IS NOT NULL, 'explicit imported bigint range number');
SELECT pg_temp.check_true(to_regclass('"Partman Test"."seven$0$1"') IS NOT NULL, 'retained leaf repaired');
SELECT pg_temp.check_inheritance('seven');
INSERT INTO "Partman Test".seven (fn, version, nv, payload) VALUES (27, 0, 40960000000000, 'imported');
SELECT pg_temp.check_true((SELECT payload = 'imported' AND extra = 17 FROM "Partman Test".seven WHERE fn = 27), 'payload/default round trip');
-- Repaired leaves at a later maintenance also inherit root indexes and defaults.
DROP TABLE "Partman Test"."three$0$1";
SELECT "naksha~admin".naksha_partman_maintain('Partman Test', 'three', 17592186044416);
SELECT pg_temp.check_inheritance('three');

CREATE TEMP TABLE removed_indexes AS
WITH RECURSIVE tree AS (
  SELECT '"Partman Test"."seven$late"'::regclass::oid AS oid
  UNION ALL SELECT h.inhrelid FROM pg_inherits h JOIN tree t ON t.oid = h.inhparent
) SELECT oid FROM tree;
DROP INDEX "Partman Test"."seven$late";
SELECT pg_temp.check_true(NOT EXISTS (SELECT 1 FROM pg_class c JOIN removed_indexes r ON r.oid = c.oid), 'root DROP INDEX removes native descendants');
SELECT "naksha~admin".naksha_partman_maintain('Partman Test', 'seven', 40960000000000);
SELECT pg_temp.check_true(NOT EXISTS (SELECT 1 FROM pg_class c JOIN removed_indexes r ON r.oid = c.oid), 'maintenance does not rebuild dropped indexes');
SELECT pg_temp.check_inheritance('seven');

-- Persisted actual tablespace and leaf storage do not depend on session defaults.
SET LOCAL default_tablespace = 'pg_default';
SELECT pg_temp.check_true(NOT EXISTS (
  SELECT 1 FROM "naksha~admin".naksha_partman r JOIN pg_partition_tree(r.root_oid) t ON t.isleaf
    JOIN pg_class c ON c.oid = t.relid JOIN pg_database d ON d.datname = current_database()
    JOIN pg_tablespace s ON s.oid = coalesce(nullif(c.reltablespace, 0), d.dattablespace)
  WHERE s.spcname <> r.tablespace_name OR NOT c.reloptions @> ARRAY[
    'toast_tuple_target=' || r.toast_tuple_target,
    'fillfactor=' || CASE WHEN c.relname LIKE '%$head%' THEN r.head_fillfactor ELSE r.history_fillfactor END
  ]
), 'all leaf storage settings and resolved tablespace');
SET LOCAL default_tablespace = '';

-- Configuration changes, malformed settings, and registry writes fail closed.
DO $test$
DECLARE
  args text;
BEGIN
  FOREACH args IN ARRAY ARRAY[
    '13,7,2048,63,95,NULL', '12,3,2048,63,95,NULL', '12,7,2049,63,95,NULL',
    '12,7,2048,64,95,NULL', '12,7,2048,63,96,NULL', '11,7,2048,63,95,NULL',
    '42,7,2048,63,95,NULL', '12,0,2048,63,95,NULL', '12,257,2048,63,95,NULL',
    '12,7,127,63,95,NULL', '12,7,2048,9,95,NULL', '12,7,2048,63,101,NULL',
    'NULL,7,2048,63,95,NULL', '12,7,NULL,63,95,NULL',
    '12,7,2048,63,95,''''', '12,7,2048,63,95,''missing_partman_space''',
    '12,7,2048,63,95,''pg_global'''
  ] LOOP
    PERFORM pg_temp.expect_failure('SELECT "naksha~admin".naksha_partman_register(''Partman Test'',''seven'',' || args || ')', '22023');
  END LOOP;
  PERFORM pg_temp.expect_failure(format('SELECT "naksha~admin".naksha_partman_register(''Partman Test'',''seven'',12,7,%s,63,95,NULL)', current_setting('block_size')::integer - 31), '22023');
END
$test$;
SELECT pg_temp.expect_failure('UPDATE "naksha~admin".naksha_partman SET shift = shift', '22023');
SELECT pg_temp.expect_failure('SELECT "naksha~admin".naksha_partman_register(''Partman Test'',repeat(''a'',30),12,1,2048,60,90,NULL)', '22023');
SELECT pg_temp.expect_failure('SELECT "naksha~admin".naksha_partman_register(repeat(''a'',64),''x'',12,1,2048,60,90,NULL)', '22023');
SELECT pg_temp.expect_failure('SELECT "naksha~admin".naksha_partman_register(''Partman Test'',''seven'',12,7,2048,63,95,repeat(''a'',64))', '22023');
SELECT pg_temp.expect_failure('SELECT "naksha~admin".naksha_partman_maintain(''Partman Test'',''seven'',-1)', '22023');
SELECT pg_temp.expect_failure('SELECT "naksha~admin".naksha_partman_maintain(''Partman Test'',''seven'',9007199254740991)', '22023');
SELECT pg_temp.expect_failure('SELECT "naksha~admin".naksha_partman_maintain(''Partman Test'',''seven'',NULL)', '22023');
SELECT pg_temp.expect_failure('SELECT "naksha~admin".naksha_partman_maintain(''Partman Test'',''absent'',0)', '22023');

-- Reject unsupported roots and missing keys without adding a registration.
CREATE TABLE "Partman Test".bad (fn bigint NOT NULL, version bigint NOT NULL, nv bigint NOT NULL) PARTITION BY RANGE (nv);
SELECT pg_temp.expect_failure('SELECT "naksha~admin".naksha_partman_register(''Partman Test'',''bad'',12,1,2048,60,90,NULL)');
SELECT pg_temp.check_true(NOT EXISTS (SELECT 1 FROM "naksha~admin".naksha_partman WHERE table_name = 'bad'), 'failed registration atomic');
SELECT pg_temp.expect_failure('SELECT "naksha~admin".naksha_partman_register(''Partman Test'',''absent'',12,1,2048,60,90,NULL)', '42P01');
CREATE TABLE "Partman Test".plain (fn bigint NOT NULL, version bigint NOT NULL, nv bigint NOT NULL);
SELECT pg_temp.expect_failure('SELECT "naksha~admin".naksha_partman_register(''Partman Test'',''plain'',12,1,2048,60,90,NULL)');
SELECT pg_temp.make_root('changed');
SELECT "naksha~admin".naksha_partman_register('Partman Test', 'changed', 12, 1, 2048, 60, 90, NULL);
DROP INDEX "Partman Test"."changed$unique";
SELECT pg_temp.expect_failure('SELECT "naksha~admin".naksha_partman_maintain(''Partman Test'',''changed'',0)');
SELECT pg_temp.check_true(to_regclass('"Partman Test"."changed$unique"') IS NULL, 'missing mandatory root index not rebuilt');
SELECT pg_temp.check_true((SELECT count(*) = 0 FROM pg_inherits WHERE inhparent = '"Partman Test".changed'::regclass), 'changed key contract rejected before provisioning');
-- Exact root byte budget and quoted identifiers with dollar characters.
SELECT pg_temp.make_root('Root$123456789012345678901234');
SELECT "naksha~admin".naksha_partman_register('Partman Test', 'Root$123456789012345678901234', 12, 1, 128, 10, 100, NULL);
SELECT "naksha~admin".naksha_partman_maintain('Partman Test', 'Root$123456789012345678901234', 9007199254740990);
SELECT pg_temp.check_inheritance('Root$123456789012345678901234');

-- Fail after some topology was created: the whole maintain call must roll back.
SELECT pg_temp.make_root('atomic');
SELECT "naksha~admin".naksha_partman_register('Partman Test', 'atomic', 12, 3, 2048, 60, 90, NULL);
CREATE TABLE "Partman Test"."atomic$2" (unrelated text);
SELECT pg_temp.expect_failure('SELECT "naksha~admin".naksha_partman_maintain(''Partman Test'',''atomic'',0)');
SELECT pg_temp.check_true((SELECT count(*) = 0 FROM pg_inherits WHERE inhparent = '"Partman Test".atomic'::regclass), 'no partial topology after conflict');
SELECT pg_temp.check_true(to_regclass('"Partman Test"."atomic$head"') IS NULL, 'HEAD creation rolled back');
DROP TABLE "Partman Test"."atomic$2";
SELECT "naksha~admin".naksha_partman_maintain('Partman Test', 'atomic', 0);
-- Wrong existing bounds, unexpected leaves, changed storage, fake HEAD checks.
ALTER TABLE "Partman Test"."atomic$head" DROP CONSTRAINT "atomic$head$check";
ALTER TABLE "Partman Test"."atomic$head" ADD CONSTRAINT "atomic$head$check" CHECK (nv = 9007199254740991 OR nv = 12);
SELECT pg_temp.expect_failure('SELECT "naksha~admin".naksha_partman_maintain(''Partman Test'',''atomic'',0)');
ALTER TABLE "Partman Test"."atomic$head" DROP CONSTRAINT "atomic$head$check";
ALTER TABLE "Partman Test"."atomic$head" ADD CONSTRAINT "atomic$head$check" CHECK (nv = 9007199254740991) NOT VALID;
SELECT pg_temp.expect_failure('SELECT "naksha~admin".naksha_partman_maintain(''Partman Test'',''atomic'',0)');
ALTER TABLE "Partman Test"."atomic$head" VALIDATE CONSTRAINT "atomic$head$check";
ALTER TABLE "Partman Test"."atomic$0$0" SET (fillfactor = 70);
SELECT pg_temp.expect_failure('SELECT "naksha~admin".naksha_partman_maintain(''Partman Test'',''atomic'',0)');
ALTER TABLE "Partman Test"."atomic$0$0" SET (fillfactor = 90);
ALTER TABLE "Partman Test"."atomic$0$0" RENAME TO "atomic$0$unexpected";
SELECT pg_temp.expect_failure('SELECT "naksha~admin".naksha_partman_maintain(''Partman Test'',''atomic'',0)');
ALTER TABLE "Partman Test"."atomic$0$unexpected" RENAME TO "atomic$0$0";
DROP TABLE "Partman Test"."atomic$0$1";
CREATE TABLE "Partman Test"."atomic$0$1" PARTITION OF "Partman Test"."atomic$0"
  FOR VALUES FROM ('-100') TO ('100') WITH (fillfactor = 90, toast_tuple_target = 2048);
SELECT pg_temp.expect_failure('SELECT "naksha~admin".naksha_partman_maintain(''Partman Test'',''atomic'',0)');
DROP TABLE "Partman Test"."atomic$0$1";
SELECT "naksha~admin".naksha_partman_maintain('Partman Test', 'atomic', 0);
SELECT pg_temp.check_inheritance('atomic');
ALTER TABLE "Partman Test"."atomic$0" RENAME TO "atomic$99";
SELECT pg_temp.expect_failure('SELECT "naksha~admin".naksha_partman_maintain(''Partman Test'',''atomic'',0)');
ALTER TABLE "Partman Test"."atomic$99" RENAME TO "atomic$0";
ALTER TABLE "Partman Test"."atomic$0$0" ADD CHECK (version >= 0);
SELECT pg_temp.expect_failure('SELECT "naksha~admin".naksha_partman_maintain(''Partman Test'',''atomic'',0)');

-- Dropped/replaced roots are never resurrected or adopted, even by register.
SELECT pg_temp.make_root('gone');
SELECT "naksha~admin".naksha_partman_register('Partman Test', 'gone', 12, 1, 2048, 60, 90, NULL);
DROP TABLE "Partman Test".gone;
SELECT pg_temp.expect_failure('SELECT "naksha~admin".naksha_partman_maintain(''Partman Test'',''gone'',0)', '42P01');
SELECT pg_temp.expect_failure('SELECT "naksha~admin".naksha_partman_register(''Partman Test'',''gone'',12,1,2048,60,90,NULL)', '42P01');
SELECT pg_temp.check_true(to_regclass('"Partman Test".gone') IS NULL, 'no dropped-root resurrection');
SELECT pg_temp.make_root('gone');
SELECT pg_temp.expect_failure('SELECT "naksha~admin".naksha_partman_maintain(''Partman Test'',''gone'',0)');
SELECT pg_temp.expect_failure('SELECT "naksha~admin".naksha_partman_register(''Partman Test'',''gone'',12,1,2048,60,90,NULL)', '22023');
SELECT pg_temp.check_true((SELECT count(*) = 0 FROM pg_inherits WHERE inhparent = '"Partman Test".gone'::regclass), 'replacement left untouched');

SELECT pg_temp.check_true(NOT EXISTS (
  SELECT 1 FROM "naksha~admin".naksha_partman r
  WHERE table_name <> 'gone' AND root_oid <> to_regclass(format('%I.%I', schema_name, table_name))::oid
), 'all original root OIDs preserved');
-- Reinstallation must preserve registrations and populated roots.
\ir NAKSHA-33-plpgsql-core-reference.sql
SELECT "naksha~admin".naksha_partman_maintain('Partman Test', 'seven', 40960000000000);
SELECT pg_temp.check_true((SELECT payload = 'imported' FROM "Partman Test".seven WHERE fn = 27), 'reinstallation preserves data');
SELECT 'PASS: production partition maintainer checks; rolling back installation and test objects' AS result;
ROLLBACK;

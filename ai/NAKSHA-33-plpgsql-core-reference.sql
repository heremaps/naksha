-- Historical PL/pgSQL core reference; production uses shared Kotlin PgPartitions.
-- One-time installation into the existing admin schema. Caller owns the transaction.
-- No root creation, version allocation, retention, scheduling, or index repair.
CREATE TABLE IF NOT EXISTS "naksha~admin".naksha_partman (
  schema_name text NOT NULL CHECK (octet_length(schema_name) BETWEEN 1 AND 63),
  table_name text NOT NULL CHECK (octet_length(table_name) BETWEEN 1 AND 29),
  shift integer NOT NULL CHECK (shift BETWEEN 12 AND 41),
  partitions integer NOT NULL CHECK (partitions BETWEEN 1 AND 256),
  root_oid oid NOT NULL UNIQUE,
  toast_tuple_target integer NOT NULL,
  head_fillfactor integer NOT NULL CHECK (head_fillfactor BETWEEN 10 AND 100),
  history_fillfactor integer NOT NULL CHECK (history_fillfactor BETWEEN 10 AND 100),
  tablespace_name text NOT NULL CHECK (octet_length(tablespace_name) BETWEEN 1 AND 63),
  PRIMARY KEY (schema_name, table_name)
);

-- Clean databases only: do not silently reuse the incompatible investigation registry.
DO $install$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_attribute WHERE attrelid = '"naksha~admin".naksha_partman'::regclass
      AND attname = 'root_oid' AND atttypid = 'pg_catalog.oid'::regtype AND NOT attisdropped
  ) THEN
    RAISE EXCEPTION 'Incompatible partition registry; production installation requires a clean database';
  END IF;
END
$install$;

CREATE OR REPLACE FUNCTION "naksha~admin".naksha_partman_immutable() RETURNS trigger
LANGUAGE plpgsql SECURITY INVOKER
SET search_path = pg_catalog, pg_temp
AS $function$
BEGIN
  RAISE EXCEPTION 'Partition registration is immutable; remove it only when dropping the collection'
    USING ERRCODE = '22023';
END
$function$;

CREATE OR REPLACE TRIGGER naksha_partman_immutable
BEFORE UPDATE ON "naksha~admin".naksha_partman
FOR EACH ROW EXECUTE FUNCTION "naksha~admin".naksha_partman_immutable();

-- Also used on every maintenance call: never repair a changed root contract.
CREATE OR REPLACE FUNCTION "naksha~admin".naksha_partman_root(p_schema text, p_table text)
RETURNS oid
LANGUAGE plpgsql SECURITY INVOKER
SET search_path = pg_catalog, pg_temp
AS $function$
DECLARE
  root oid;
  fn_att smallint;
  nv_att smallint;
  version_att smallint;
BEGIN
  IF p_schema IS NULL OR octet_length(p_schema) NOT BETWEEN 1 AND 63
    OR p_table IS NULL OR octet_length(p_table) NOT BETWEEN 1 AND 29 THEN
    RAISE EXCEPTION 'Schema/root names must fit 63/29 bytes' USING ERRCODE = '22023';
  END IF;
  root := to_regclass(format('%I.%I', p_schema, p_table));
  IF root IS NULL THEN
    RAISE EXCEPTION 'Partition root %.% is missing', p_schema, p_table USING ERRCODE = '42P01';
  END IF;
  -- Prevent concurrent DROP/ALTER while inspecting and provisioning this root.
  EXECUTE format('LOCK TABLE %I.%I IN ACCESS EXCLUSIVE MODE', p_schema, p_table);
  IF to_regclass(format('%I.%I', p_schema, p_table))::oid IS DISTINCT FROM root THEN
    RAISE EXCEPTION 'Partition root %.% changed while acquiring its lock', p_schema, p_table;
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_class WHERE oid = root AND relkind = 'p'
      AND relpersistence = 'p' AND NOT relispartition
  ) OR pg_get_partkeydef(root) IS DISTINCT FROM 'RANGE (nv)' THEN
    RAISE EXCEPTION 'Root %.% must be permanent, standalone RANGE (nv)', p_schema, p_table;
  END IF;
  SELECT attnum INTO fn_att FROM pg_attribute
    WHERE attrelid = root AND attname = 'fn' AND atttypid = 'pg_catalog.int8'::regtype
      AND attnotnull AND NOT attisdropped AND attgenerated = '';
  SELECT attnum INTO nv_att FROM pg_attribute
    WHERE attrelid = root AND attname = 'nv' AND atttypid = 'pg_catalog.int8'::regtype
      AND attnotnull AND NOT attisdropped AND attgenerated = '';
  SELECT attnum INTO version_att FROM pg_attribute
    WHERE attrelid = root AND attname = 'version' AND atttypid = 'pg_catalog.int8'::regtype
      AND attnotnull AND NOT attisdropped AND attgenerated = '';
  IF fn_att IS NULL OR nv_att IS NULL OR version_att IS NULL THEN
    RAISE EXCEPTION 'Root requires bigint NOT NULL fn, nv, version';
  END IF;
  IF EXISTS (
    SELECT 1 FROM pg_constraint WHERE conrelid = root AND contype = 'c'
      AND (NOT convalidated OR connoinherit)
  ) THEN
    RAISE EXCEPTION 'Root check constraints must be validated and inheritable';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_index i JOIN pg_constraint c ON c.conindid = i.indexrelid
    WHERE i.indrelid = root AND c.contype = 'p' AND NOT c.condeferrable
      AND i.indisvalid AND i.indisready AND i.indnkeyatts = 3
      AND i.indexprs IS NULL AND i.indpred IS NULL
      AND ARRAY[i.indkey[0], i.indkey[1], i.indkey[2]] = ARRAY[nv_att, fn_att, version_att]
  ) OR NOT EXISTS (
    SELECT 1 FROM pg_index i WHERE i.indrelid = root
      AND i.indisunique AND i.indisvalid AND i.indisready AND i.indimmediate
      AND i.indnkeyatts = 2 AND i.indnatts = 3
      AND i.indexprs IS NULL AND i.indpred IS NULL
      AND ARRAY[i.indkey[0], i.indkey[1], i.indkey[2]] = ARRAY[fn_att, nv_att, version_att]
  ) THEN
    RAISE EXCEPTION 'Root requires PRIMARY KEY (nv, fn, version) and UNIQUE (fn, nv) INCLUDE (version)';
  END IF;
  RETURN root;
END
$function$;

CREATE OR REPLACE FUNCTION "naksha~admin".naksha_partman_register(
  p_schema text, p_table text, p_shift integer, p_partitions integer,
  p_toast integer, p_head_fillfactor integer, p_history_fillfactor integer, p_tablespace text
) RETURNS void
LANGUAGE plpgsql SECURITY INVOKER
SET search_path = pg_catalog, pg_temp
AS $function$
DECLARE
  root oid;
  space_name text;
  registered "naksha~admin".naksha_partman%ROWTYPE;
BEGIN
  -- PostgreSQL's toast_tuple_target reloption is 128..MaximumBytesPerTuple(1).
  -- Supported PostgreSQL builds use an aligned 24-byte page header plus one item.
  IF p_shift IS NULL OR p_shift NOT BETWEEN 12 AND 41
    OR p_schema IS NULL OR octet_length(p_schema) NOT BETWEEN 1 AND 63
    OR p_table IS NULL OR octet_length(p_table) NOT BETWEEN 1 AND 29
    OR p_partitions IS NULL OR p_partitions NOT BETWEEN 1 AND 256
    OR p_toast IS NULL OR p_toast NOT BETWEEN 128 AND current_setting('block_size')::integer - 32
    OR p_head_fillfactor IS NULL OR p_head_fillfactor NOT BETWEEN 10 AND 100
    OR p_history_fillfactor IS NULL OR p_history_fillfactor NOT BETWEEN 10 AND 100
    OR (p_tablespace IS NOT NULL AND octet_length(p_tablespace) NOT BETWEEN 1 AND 63) THEN
    RAISE EXCEPTION 'Invalid partition/storage settings' USING ERRCODE = '22023';
  END IF;
  PERFORM pg_advisory_xact_lock(hashtextextended(format('naksha:partman:%I.%I', p_schema, p_table), 0));
  root := "naksha~admin".naksha_partman_root(p_schema, p_table);
  IF p_tablespace IS NULL THEN
    SELECT t.spcname INTO space_name
    FROM pg_class c JOIN pg_database d ON d.datname = current_database()
      JOIN pg_tablespace t ON t.oid = CASE WHEN c.reltablespace = 0 THEN d.dattablespace ELSE c.reltablespace END
    WHERE c.oid = root;
  ELSE
    SELECT spcname INTO space_name FROM pg_tablespace WHERE spcname = p_tablespace;
  END IF;
  IF space_name IS NULL OR space_name = 'pg_global' THEN
    RAISE EXCEPTION 'Missing or unsupported tablespace %', p_tablespace USING ERRCODE = '22023';
  END IF;
  SELECT * INTO registered FROM "naksha~admin".naksha_partman
    WHERE schema_name = p_schema AND table_name = p_table;
  IF FOUND THEN
    IF ROW(registered.root_oid, registered.shift, registered.partitions,
      registered.toast_tuple_target, registered.head_fillfactor,
      registered.history_fillfactor, registered.tablespace_name)
      IS DISTINCT FROM ROW(root, p_shift, p_partitions, p_toast,
        p_head_fillfactor, p_history_fillfactor, space_name) THEN
      RAISE EXCEPTION 'Conflicting immutable partition registration for %.%', p_schema, p_table
        USING ERRCODE = '22023';
    END IF;
    RETURN;
  END IF;
  INSERT INTO "naksha~admin".naksha_partman VALUES (
    p_schema, p_table, p_shift, p_partitions, root, p_toast,
    p_head_fillfactor, p_history_fillfactor, space_name
  );
END
$function$;

-- Strict topology validation shared by nv children and fn leaves. No adoption of
-- same-named unrelated objects, extra columns, or locally changed root checks.
CREATE OR REPLACE FUNCTION "naksha~admin".naksha_partman_check_child(
  p_schema text, p_name text, p_parent oid, p_bound text, p_partitioned boolean
) RETURNS oid
LANGUAGE plpgsql SECURITY INVOKER
SET search_path = pg_catalog, pg_temp
AS $function$
DECLARE
  child oid := to_regclass(format('%I.%I', p_schema, p_name));
BEGIN
  IF child IS NULL OR NOT EXISTS (
    SELECT 1 FROM pg_class c JOIN pg_inherits i ON i.inhrelid = c.oid
    WHERE c.oid = child AND i.inhparent = p_parent AND NOT i.inhdetachpending
      AND c.relispartition AND c.relpersistence = 'p'
      AND c.relkind = CASE WHEN p_partitioned THEN 'p'::"char" ELSE 'r'::"char" END
      AND pg_get_expr(c.relpartbound, c.oid) = p_bound
  ) OR (SELECT count(*) FROM pg_inherits WHERE inhrelid = child) <> 1
    OR (NOT p_partitioned AND EXISTS (SELECT 1 FROM pg_inherits WHERE inhparent = child))
    OR (p_partitioned AND pg_get_partkeydef(child) IS DISTINCT FROM 'RANGE (fn)') THEN
    RAISE EXCEPTION 'Conflicting partition %.% (parent %, bound %)', p_schema, p_name, p_parent, p_bound;
  END IF;
  IF EXISTS (
    (SELECT attname, atttypid, atttypmod, attcollation, attnotnull, attgenerated
      FROM pg_attribute WHERE attrelid = child AND attnum > 0 AND NOT attisdropped
    EXCEPT
    SELECT attname, atttypid, atttypmod, attcollation, attnotnull, attgenerated
      FROM pg_attribute WHERE attrelid = p_parent AND attnum > 0 AND NOT attisdropped)
    UNION ALL
    (SELECT attname, atttypid, atttypmod, attcollation, attnotnull, attgenerated
      FROM pg_attribute WHERE attrelid = p_parent AND attnum > 0 AND NOT attisdropped
    EXCEPT
    SELECT attname, atttypid, atttypmod, attcollation, attnotnull, attgenerated
      FROM pg_attribute WHERE attrelid = child AND attnum > 0 AND NOT attisdropped)
  ) OR EXISTS (
    SELECT 1 FROM pg_constraint p WHERE p.conrelid = p_parent AND p.contype = 'c'
      AND (NOT p.convalidated OR p.connoinherit OR NOT EXISTS (
        SELECT 1 FROM pg_constraint c WHERE c.conrelid = child AND c.contype = 'c'
          AND c.conname = p.conname AND c.convalidated AND c.coninhcount > 0
          AND pg_get_expr(c.conbin, c.conrelid) = pg_get_expr(p.conbin, p.conrelid)
      ))
  ) OR EXISTS (
    SELECT 1 FROM pg_constraint c WHERE c.conrelid = child AND c.contype = 'c'
      AND c.coninhcount = 0 AND NOT (
        p_bound = 'DEFAULT' AND c.convalidated AND NOT c.connoinherit
        AND pg_get_expr(c.conbin, c.conrelid) = '(nv = ''9007199254740991''::bigint)'
      )
  ) THEN
    RAISE EXCEPTION 'Changed inherited column/check contract on %.%', p_schema, p_name;
  END IF;
  RETURN child;
END
$function$;

CREATE OR REPLACE FUNCTION "naksha~admin".naksha_partman_maintain(
  p_schema text, p_table text, p_version bigint
) RETURNS void
LANGUAGE plpgsql SECURITY INVOKER
SET search_path = pg_catalog, pg_temp
AS $function$
DECLARE
  head constant bigint := 9007199254740991;
  registered "naksha~admin".naksha_partman%ROWTYPE;
  root oid;
  child oid;
  leaf oid;
  existing record;
  width bigint;
  start_nv bigint;
  end_nv bigint;
  range_number bigint;
  range_suffix text;
  child_name text;
  leaf_name text;
  expected_bound text;
  low_fn text;
  high_fn text;
  step integer;
  slot integer;
  fill integer;
  space_oid oid;
BEGIN
  IF p_version IS NULL OR p_version < 0 OR p_version >= head
    OR p_schema IS NULL OR octet_length(p_schema) NOT BETWEEN 1 AND 63
    OR p_table IS NULL OR octet_length(p_table) NOT BETWEEN 1 AND 29 THEN
    RAISE EXCEPTION 'Maintenance requires valid schema/root names and a finite version in 0..HEAD-1' USING ERRCODE = '22023';
  END IF;
  PERFORM pg_advisory_xact_lock(hashtextextended(format('naksha:partman:%I.%I', p_schema, p_table), 0));
  SELECT * INTO registered FROM "naksha~admin".naksha_partman
    WHERE schema_name = p_schema AND table_name = p_table;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Unregistered partition root %.%', p_schema, p_table USING ERRCODE = '22023';
  END IF;
  root := "naksha~admin".naksha_partman_root(p_schema, p_table);
  IF root <> registered.root_oid THEN
    RAISE EXCEPTION 'Registered root %.% has been replaced', p_schema, p_table;
  END IF;
  SELECT oid INTO space_oid FROM pg_tablespace WHERE spcname = registered.tablespace_name;
  IF space_oid IS NULL OR registered.tablespace_name = 'pg_global' THEN
    RAISE EXCEPTION 'Registered tablespace % is missing or unsupported', registered.tablespace_name;
  END IF;
  width := 1::bigint << registered.shift;

  -- Validate every retained child before adding coverage, not just current/future.
  -- A second pass below includes newly created children and repairs missing leaves.
  FOR step IN 0..1 LOOP
    IF step = 1 THEN
      FOR slot IN -1..3 LOOP
        IF slot = -1 THEN
          child_name := p_table || '$head';
          expected_bound := 'DEFAULT';
        ELSE
          start_nv := (p_version >> registered.shift) * width + slot::bigint * width;
          IF start_nv >= head THEN CONTINUE; END IF;
          end_nv := least(start_nv + width, head);
          child_name := p_table || '$' || (start_nv >> registered.shift)::text;
          expected_bound := format('FOR VALUES FROM (%L) TO (%L)', start_nv, end_nv);
        END IF;
        child := to_regclass(format('%I.%I', p_schema, child_name));
        IF child IS NULL THEN
          -- Physical leaves always receive explicit storage and tablespace. A
          -- partitioned intermediary has no heap reloptions or storage of its own.
          EXECUTE format('CREATE TABLE %I.%I PARTITION OF %I.%I %s %s %s %s',
            p_schema, child_name, p_schema, p_table,
            CASE WHEN slot = -1 THEN format('(CONSTRAINT %I CHECK (nv = %s::bigint))', child_name || '$check', head) ELSE '' END,
            CASE WHEN slot = -1 THEN 'DEFAULT' ELSE format('FOR VALUES FROM (%s) TO (%s)', start_nv, end_nv) END,
            CASE WHEN registered.partitions > 1 THEN 'PARTITION BY RANGE (fn)'
              ELSE format('WITH (fillfactor = %s, toast_tuple_target = %s)',
                CASE WHEN slot = -1 THEN registered.head_fillfactor ELSE registered.history_fillfactor END,
                registered.toast_tuple_target) END,
            CASE WHEN registered.partitions > 1 THEN '' ELSE format('TABLESPACE %I', registered.tablespace_name) END);
        END IF;
        PERFORM "naksha~admin".naksha_partman_check_child(
          p_schema, child_name, root, expected_bound, registered.partitions > 1);
      END LOOP;
    END IF;

    FOR existing IN
      SELECT c.oid, c.relname, n.nspname FROM pg_inherits i
        JOIN pg_class c ON c.oid = i.inhrelid JOIN pg_namespace n ON n.oid = c.relnamespace
      WHERE i.inhparent = root
    LOOP
      child_name := existing.relname;
      IF existing.nspname <> p_schema THEN
        RAISE EXCEPTION 'Unexpected cross-schema child %', existing.oid;
      END IF;
      IF child_name = p_table || '$head' THEN
        expected_bound := 'DEFAULT';
        fill := registered.head_fillfactor;
      ELSE
        range_suffix := substr(child_name, length(p_table) + 2);
        IF left(child_name, length(p_table) + 1) <> p_table || '$'
          OR range_suffix !~ '^(0|[1-9][0-9]*)$' OR length(range_suffix) > 16 THEN
          RAISE EXCEPTION 'Unexpected nv child %', child_name;
        END IF;
        range_number := range_suffix::bigint;
        IF range_number > ((head - 1) >> registered.shift) THEN
          RAISE EXCEPTION 'Out-of-domain nv child %', child_name;
        END IF;
        start_nv := range_number * width;
        end_nv := least(start_nv + width, head);
        expected_bound := format('FOR VALUES FROM (%L) TO (%L)', start_nv, end_nv);
        fill := registered.history_fillfactor;
      END IF;
      child := "naksha~admin".naksha_partman_check_child(
        p_schema, child_name, root, expected_bound, registered.partitions > 1);
      IF child_name = p_table || '$head' AND NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conrelid = child AND contype = 'c' AND convalidated
          AND NOT connoinherit AND pg_get_expr(conbin, conrelid) = '(nv = ''9007199254740991''::bigint)'
      ) THEN
        RAISE EXCEPTION 'HEAD requires an exact validated nv = HEAD check on %.%', p_schema, child_name;
      END IF;
      IF registered.partitions = 1 THEN
        IF NOT EXISTS (
          SELECT 1 FROM pg_class c JOIN pg_database d ON d.datname = current_database()
          WHERE c.oid = child AND coalesce(nullif(c.reltablespace, 0), d.dattablespace) = space_oid
            AND c.reloptions @> ARRAY['fillfactor=' || fill, 'toast_tuple_target=' || registered.toast_tuple_target]
        ) THEN
          RAISE EXCEPTION 'Changed leaf storage on %.%', p_schema, child_name;
        END IF;
        CONTINUE;
      END IF;
      IF EXISTS (
        SELECT 1 FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid
          JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE i.inhparent = child AND (n.nspname <> p_schema OR NOT EXISTS (
          SELECT 1 FROM generate_series(0, registered.partitions - 1) s
          WHERE c.relname = child_name || '$' || s::text
        ))
      ) THEN
        RAISE EXCEPTION 'Unexpected fn leaf under %.%', p_schema, child_name;
      END IF;
      FOR slot IN 0..registered.partitions - 1 LOOP
        low_fn := CASE WHEN slot = 0 THEN 'MINVALUE' ELSE
          (-9223372036854775808::numeric
            + (slot * 256 / registered.partitions)::numeric * 72057594037927936)::text END;
        high_fn := CASE WHEN slot = registered.partitions - 1 THEN 'MAXVALUE' ELSE
          (-9223372036854775808::numeric
            + ((slot + 1) * 256 / registered.partitions)::numeric * 72057594037927936)::text END;
        expected_bound := format('FOR VALUES FROM (%s) TO (%s)',
          CASE WHEN slot = 0 THEN low_fn ELSE quote_literal(low_fn) END,
          CASE WHEN slot = registered.partitions - 1 THEN high_fn ELSE quote_literal(high_fn) END);
        leaf_name := child_name || '$' || slot;
        leaf := to_regclass(format('%I.%I', p_schema, leaf_name));
        IF leaf IS NULL AND step = 0 THEN CONTINUE; END IF;
        IF leaf IS NULL THEN
          EXECUTE format('CREATE TABLE %I.%I PARTITION OF %I.%I FOR VALUES FROM (%s) TO (%s) WITH (fillfactor = %s, toast_tuple_target = %s) TABLESPACE %I',
            p_schema, leaf_name, p_schema, child_name, low_fn, high_fn,
            fill, registered.toast_tuple_target, registered.tablespace_name);
        END IF;
        leaf := "naksha~admin".naksha_partman_check_child(p_schema, leaf_name, child, expected_bound, false);
        IF NOT EXISTS (
          SELECT 1 FROM pg_class c JOIN pg_database d ON d.datname = current_database()
          WHERE c.oid = leaf AND coalesce(nullif(c.reltablespace, 0), d.dattablespace) = space_oid
            AND c.reloptions @> ARRAY['fillfactor=' || fill, 'toast_tuple_target=' || registered.toast_tuple_target]
        ) THEN
          RAISE EXCEPTION 'Changed leaf storage on %.%', p_schema, leaf_name;
        END IF;
      END LOOP;
    END LOOP;
  END LOOP;
END
$function$;

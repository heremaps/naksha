CREATE EXTENSION IF NOT EXISTS plv8;

CREATE SCHEMA IF NOT EXISTS public;
CREATE SCHEMA IF NOT EXISTS topology;
CREATE SCHEMA IF NOT EXISTS "${schema}";
SET SESSION search_path TO "${schema}", public, topology;

CREATE EXTENSION IF NOT EXISTS btree_gist SCHEMA public;
CREATE EXTENSION IF NOT EXISTS btree_gin SCHEMA public;
CREATE EXTENSION IF NOT EXISTS postgis SCHEMA public;
CREATE EXTENSION IF NOT EXISTS postgis_topology SCHEMA topology;
-- Restore search_path, because postgis_topology modifies it.
SET SESSION search_path TO "${schema}", public, topology;

-- This is optional
--pg_hint_plan:CREATE SCHEMA IF NOT EXISTS hint_plan;
--pg_hint_plan:CREATE EXTENSION IF NOT EXISTS pg_hint_plan SCHEMA hint_plan;
-- Restore search_path, because hint_plan modifies it.
--pg_hint_plan:SET SESSION search_path TO "${schema}", public, topology;

COMMIT;

CREATE OR REPLACE FUNCTION naksha_txn(ts timestamptz, id int8) RETURNS int8 LANGUAGE 'plpgsql' IMMUTABLE AS $$
BEGIN
  RETURN (extract('year' FROM ts) * 10000 + extract('month' FROM ts) * 100 + extract('day' FROM ts)) * 100000000000 + id;
END $$;

-- Returns current transaction number, i.e. 2023100600000000010, which is build as yyyyMMddXXXXXXXXXXX.
CREATE OR REPLACE FUNCTION naksha_txn() RETURNS int8 LANGUAGE 'plpgsql' STABLE AS $$
DECLARE
  LOCK_ID constant int8 := nk_lock_id('naksha_tx_object_id_seq');
  SEQ_DIVIDER constant int8 := 100000000000;
  value    text;
  txi      int8;
  txn      int8;
  tx_date  int4;
  seq_date int4;
BEGIN
  value := current_setting('naksha.txn', true);
  IF coalesce(value, '') <> '' THEN
--RAISE NOTICE 'found value = %', value;
    return value::int8;
  END IF;

  -- prepare current yyyyMMdd as number i.e. 20231006
  tx_date := extract('year' from current_timestamp) * 10000 + extract('month' from current_timestamp) * 100 + extract('day' from current_timestamp);

  txi := nextval('naksha_txn_id_seq');
  -- txi should start with current date  20231006 with seq number "at the end"
  -- example: 2023100600000000007
  seq_date := txi / SEQ_DIVIDER; -- returns as number seq prefix which is yyyyMMdd  i.e. 20231006

  -- verification, if current day is not same as day in txi we have to reset sequence to new date and counting from start.
  IF seq_date <> tx_date then
    -- not sure if this lock it's enough, 'cause this is session lock that might be acquired multiple times within same session
    -- it has to be discussed if there is a chance of calling this function multiple times in parallel in same session.
    PERFORM pg_advisory_lock(LOCK_ID);
    BEGIN
      txi := nextval('naksha_txn_id_seq');
      seq_date := txi / SEQ_DIVIDER ;

      IF seq_date <> tx_date then
          txn := tx_date * SEQ_DIVIDER;
          -- is_called set to true guarantee that next val will be +1
          PERFORM setval('naksha_txn_id_seq', txn, true);
      ELSE
          txn := txi;
      END IF;
      PERFORM pg_advisory_unlock(LOCK_ID);
    EXCEPTION WHEN OTHERS THEN
      PERFORM pg_advisory_unlock(LOCK_ID);
      RAISE;
    END;
  ELSE
    txn := txi;
  END IF;
  PERFORM SET_CONFIG('naksha.txn', txn::text, true);
  RETURN txn::int8;
END $$;

-- Returns the packed Naksha extension version: 16 bit reserved, 16 bit major, 16 bit minor, 16 bit revision.
CREATE OR REPLACE FUNCTION naksha_version() RETURNS int8 LANGUAGE 'plpgsql' IMMUTABLE AS $$ BEGIN
  RETURN ${version};
END $$;

-- Returns the storage-id of this storage, this is created when the Naksha extension is installed and never changes.
CREATE OR REPLACE FUNCTION naksha_storage_id() RETURNS text LANGUAGE 'plpgsql' IMMUTABLE AS $$ BEGIN
  RETURN '${storage_id}';
END $$;

-- Returns the schema of this storage, this is created when the Naksha extension is installed and never changes.
CREATE OR REPLACE FUNCTION naksha_schema() RETURNS text LANGUAGE 'plpgsql' IMMUTABLE AS $$ BEGIN
  RETURN '${schema}';
END $$;

-- TODO: naksha_init_storage()
--       create transaction counter
--       create partitioned transaction table
--       create global dictionary table
--       create methods that synchronize on the counter and ensure day-wise counter
--       each transaction should hold a list with collections being modified
--       only one row per transaction!
--       Allow one arbitrary JBON attachment per transaction

CREATE OR REPLACE FUNCTION naksha_start_session(app_name text, stream_id text, app_id text, author text) RETURNS void AS $$
  if (typeof require !== "function") {
    var commonjs2_init = plv8.find_function("commonjs2_init");
    commonjs2_init();
    if (typeof require !== "function") {
      plv8.elog(ERROR, "Failed to initialize module system");
    }
  }
  let naksha = require("naksha");
  let jb = require("jbon");
  let env = naksha.Plv8Env.Companion.get();
  let session = new naksha.NakshaSession(new naksha.Plv8Sql(), '${schema}', '${storage_id}', app_name, stream_id, app_id, author);
  jb.JbSession.Companion.threadLocal.set(session);
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION naksha_init_storage() RETURNS void AS $$
  let naksha = require("naksha");
  let jb = require("jbon");
  let session = jb.NakshaSession.Companion.get();
  session.initStorage();
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION naksha_trigger_before() RETURNS trigger AS $$
  let naksha = require("naksha");
  // TODO: Clarify if TG_RELID is double or bigint!
  let t = new naksha.PgTrigger(TG_OP, TG_NAME, TG_WHEN, TG_LEVEL, TG_RELID, TG_TABLE_NAME, TG_TABLE_SCHEMA, NEW, OLD);
  let session = naksha.NakshaSession.get();
  session.triggerBefore(t);
  if (TG_OP == "INSERT" || TG_OP == "UPDATE") {
    return NEW
  }
  return OLD;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION naksha_trigger_after() RETURNS trigger AS $$
  let naksha = require("naksha");
  // TODO: Clarify if TG_RELID is double or bigint!
  let t = new naksha.PgTrigger(TG_OP, TG_NAME, TG_WHEN, TG_LEVEL, TG_RELID, TG_TABLE_NAME, TG_TABLE_SCHEMA, NEW, OLD);
  let session = naksha.NakshaSession.get();
  session.triggerAfter(t);
  if (TG_OP == "INSERT" || TG_OP == "UPDATE") {
    return NEW
  }
  return OLD;
$$ LANGUAGE 'plv8' IMMUTABLE;

-- Returns always op, id, xyz, optional: geo, feature, tags, err_no and err_msg
CREATE OR REPLACE FUNCTION naksha_write_features(
  collection_id text,
  ops bytea[], -- XyzOp (op, id, uuid, crid)
  geometries geometry[], -- WKB
  features bytea[], -- JbFeature
  tags bytea[] -- XyzTags
) RETURNS TABLE (op text, id text, xyz bytea, tags bytea, geo geometry, feature bytea, err_no text, err_msg text) AS $$
  let naksha = require("naksha");
  let session = naksha.NakshaSession.get();
  session.writeFeatures(collection_id, ops, geometries, features, tags);
$$ LANGUAGE 'plv8' IMMUTABLE;

-- Returns always op, id, xyz, optional: geo, feature, tags, err_no and err_msg
CREATE OR REPLACE FUNCTION naksha_write_collections(
  ops bytea[], -- XyzOp (op, id, uuid, crid)
  geometries geometry[], -- WKB
  features bytea[], -- JbFeature
  tags bytea[] -- XyzTags
) RETURNS TABLE (op text, id text, xyz bytea, tags bytea, geo geometry, feature bytea, err_no text, err_msg text) AS $$
  let naksha = require("naksha");
  let session = naksha.NakshaSession.get();
  session.writeCollections(ops, geometries, features, tags);
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION naksha_err_no() RETURNS text AS $$
  let naksha = require("naksha");
  let session = naksha.NakshaSession.get();
  return session.errNo;
$$ LANGUAGE 'plv8' VOLATILE;

CREATE OR REPLACE FUNCTION naksha_err_msg() RETURNS text AS $$
  let naksha = require("naksha");
  let session = naksha.NakshaSession.get();
  return session.errMsg;
$$ LANGUAGE 'plv8' VOLATILE;

CREATE OR REPLACE FUNCTION naksha_partition_number(id text) RETURNS text AS $$
  let naksha = require("naksha");
  let session = naksha.NakshaSession.get();
  return session.partitionNumber(id);
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION naksha_partition_id(id text) RETURNS text AS $$
  let naksha = require("naksha");
  let session = naksha.NakshaSession.get();
  return session.partitionId(id);
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_created_at(xyz bytea) RETURNS double precision AS $$
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_updated_at(xyz bytea) RETURNS double precision AS $$
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_ts(xyz bytea) RETURNS double precision AS $$
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_ts(xyz bytea) RETURNS double precision AS $$
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_tnx(xyz bytea) RETURNS double precision AS $$
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_tnx_next(xyz bytea) RETURNS double precision AS $$
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_version(xyz bytea) RETURNS int4 AS $$
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_author_ts(xyz bytea) RETURNS double precision AS $$
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_uid(xyz bytea) RETURNS int8 AS $$
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_extend(xyz bytea) RETURNS int8 AS $$
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_author(xyz bytea) RETURNS text AS $$
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_app_id(xyz bytea) RETURNS text AS $$
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_uuid(feature bytea) RETURNS text AS $$
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_puuid(feature bytea) RETURNS text AS $$
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_action(feature bytea) RETURNS text AS $$
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_crid(feature bytea) RETURNS text AS $$
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_grid(feature bytea) RETURNS text AS $$
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_mrid(feature bytea) RETURNS text AS $$
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_tags(feature bytea) RETURNS jsonb AS $$
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_tags_map(feature bytea) RETURNS jsonb AS $$
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION naksha_feature_id(feature bytea) RETURNS text AS $$
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION naksha_feature_type(feature bytea) RETURNS text AS $$
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION naksha_feature_ptype(feature bytea) RETURNS text AS $$
$$ LANGUAGE 'plv8' IMMUTABLE;

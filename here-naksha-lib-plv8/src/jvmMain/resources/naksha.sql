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

CREATE OR REPLACE FUNCTION naksha_start_session(app_name text, stream_id text, app_id text, author text) RETURNS void AS $$
  if (typeof require !== "function") {
    var commonjs2_init = plv8.find_function("commonjs2_init");
    commonjs2_init();
    if (typeof require !== "function") {
      plv8.elog(ERROR, "Failed to initialize module system");
    }
  }
  let naksha = require("naksha");
  naksha.JsPlv8Env.Companion.initialize();
  let jb = require("jbon");
  let session = jb.JbSession.Companion.threadLocal.get();
  if (session != null) {
    session.reset('${schema}', '${storage_id}', app_name, stream_id, app_id, author);
  } else {
    session = new naksha.NakshaSession(new naksha.JsPlv8Sql(), '${schema}', '${storage_id}', app_name, stream_id, app_id, author);
    jb.JbSession.Companion.threadLocal.set(session);
  }
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION naksha_trigger_before() RETURNS trigger AS $$
  let naksha = require("naksha");
  let asMap = require("jbon").Jb.map.asMap;
  // TODO: Clarify if TG_RELID is double or bigint!
  let t = new naksha.PgTrigger(TG_OP, TG_NAME, TG_WHEN, TG_LEVEL, TG_RELID, TG_TABLE_NAME, TG_TABLE_SCHEMA, asMap(NEW), asMap(OLD));
  let session = naksha.NakshaSession.Companion.get();
  session.triggerBefore(t);
  if (TG_OP == "INSERT" || TG_OP == "UPDATE") {
    return NEW
  }
  return OLD;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION naksha_trigger_after() RETURNS trigger AS $$
  let naksha = require("naksha");
  let asMap = require("jbon").Jb.map.asMap;
  // TODO: Clarify if TG_RELID is double or bigint!
  let t = new naksha.PgTrigger(TG_OP, TG_NAME, TG_WHEN, TG_LEVEL, TG_RELID, TG_TABLE_NAME, TG_TABLE_SCHEMA, asMap(NEW), asMap(OLD));
  let session = naksha.NakshaSession.Companion.get();
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
  features bytea[], -- JbFeature
  geometries geometry[], -- WKB
  tags bytea[] -- XyzTags
) RETURNS TABLE (op text, id text, xyz bytea, tags bytea, feature bytea, geo geometry, err_no text, err_msg text) AS $$
  let naksha = require("naksha");
  let session = naksha.NakshaSession.Companion.get();
  session.writeFeatures(collection_id, ops, geometries, features, tags);
$$ LANGUAGE 'plv8' IMMUTABLE;

-- Returns always op, id, xyz, optional: geo, feature, tags, err_no and err_msg
CREATE OR REPLACE FUNCTION naksha_write_collections(
  ops bytea[], -- XyzOp (op, id, uuid, crid)
  features bytea[], -- JbFeature
  geometries geometry[], -- WKB
  tags bytea[] -- XyzTags
) RETURNS TABLE (op text, id text, xyz bytea, tags bytea, feature bytea, geo geometry, err_no text, err_msg text) AS $$
  let naksha = require("naksha");
  let session = naksha.NakshaSession.Companion.get();
  session.writeCollections(ops, geometries, features, tags);
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION naksha_err_no() RETURNS text AS $$
  let naksha = require("naksha");
  let session = naksha.NakshaSession.Companion.get();
  return session.errNo;
$$ LANGUAGE 'plv8' VOLATILE;

CREATE OR REPLACE FUNCTION naksha_err_msg() RETURNS text AS $$
  let naksha = require("naksha");
  let session = naksha.NakshaSession.Companion.get();
  return session.errMsg;
$$ LANGUAGE 'plv8' VOLATILE;

CREATE OR REPLACE FUNCTION naksha_partition_number(id text) RETURNS int4 AS $$
  return require("naksha").Naksha.partitionNumber(id);
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION naksha_partition_id(id text) RETURNS text AS $$
  return require("naksha").Naksha.partitionId(id);
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_created_at(xyz bytea) RETURNS int8 AS $$
  let naksha = require("naksha");
  let xyzNs = new naksha.XyzNs();
  xyzNs.mapBytes(xyz);
  return xyzNs.createdAt();
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_updated_at(xyz bytea) RETURNS int8 AS $$
  let naksha = require("naksha");
  let xyzNs = new naksha.XyzNs();
  xyzNs.mapBytes(xyz);
  return xyzNs.updatedAt();
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_txn(xyz bytea) RETURNS int8 AS $$
  let naksha = require("naksha");
  let xyzNs = new naksha.XyzNs();
  xyzNs.mapBytes(xyz);
  return xyzNs.txn().value;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_version(xyz bytea) RETURNS int4 AS $$
  let naksha = require("naksha");
  let xyzNs = new naksha.XyzNs();
  xyzNs.mapBytes(xyz);
  return xyzNs.version();
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_extend(xyz bytea) RETURNS int8 AS $$
  let naksha = require("naksha");
  let xyzNs = new naksha.XyzNs();
  xyzNs.mapBytes(xyz);
  return xyzNs.extend();
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_author(xyz bytea) RETURNS text AS $$
  let naksha = require("naksha");
  let xyzNs = new naksha.XyzNs();
  xyzNs.mapBytes(xyz);
  return xyzNs.author();
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_author_ts(xyz bytea) RETURNS int8 AS $$
  let naksha = require("naksha");
  let xyzNs = new naksha.XyzNs();
  xyzNs.mapBytes(xyz);
  return xyzNs.authorTs();
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_app_id(xyz bytea) RETURNS text AS $$
  let naksha = require("naksha");
  let xyzNs = new naksha.XyzNs();
  xyzNs.mapBytes(xyz);
  return xyzNs.appId();
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_uuid(xyz bytea) RETURNS text AS $$
  let naksha = require("naksha");
  let xyzNs = new naksha.XyzNs();
  xyzNs.mapBytes(xyz);
  return xyzNs.uuid();
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_puuid(xyz bytea) RETURNS text AS $$
  let naksha = require("naksha");
  let xyzNs = new naksha.XyzNs();
  xyzNs.mapBytes(xyz);
  return xyzNs.puuid();
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_action(xyz bytea) RETURNS text AS $$
  let naksha = require("naksha");
  let xyzNs = new naksha.XyzNs();
  xyzNs.mapBytes(xyz);
  return xyzNs.actionAsString();
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_crid(xyz bytea) RETURNS text AS $$
  let naksha = require("naksha");
  let xyzNs = new naksha.XyzNs();
  xyzNs.mapBytes(xyz);
  return xyzNs.crid();
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_grid(xyz bytea) RETURNS text AS $$
  let naksha = require("naksha");
  let xyzNs = new naksha.XyzNs();
  xyzNs.mapBytes(xyz);
  return xyzNs.grid();
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_mrid(xyz bytea) RETURNS text AS $$
  let naksha = require("naksha");
  let xyzNs = new naksha.XyzNs();
  xyzNs.mapBytes(xyz);
  return xyzNs.mrid();
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION tags_to_jsonb(tags bytea) RETURNS jsonb AS $$
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION naksha_feature_id(feature bytea) RETURNS text AS $$
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION naksha_feature_type(feature bytea) RETURNS text AS $$
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION naksha_feature_ptype(feature bytea) RETURNS text AS $$
$$ LANGUAGE 'plv8' IMMUTABLE;

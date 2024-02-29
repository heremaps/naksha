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

CREATE OR REPLACE FUNCTION naksha_txn() RETURNS int8 AS $$
  let naksha = require("naksha");
  let session = naksha.NakshaSession.Companion.get();
  return session.txn().value;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION naksha_trigger_before() RETURNS trigger AS $$
  let naksha = require("naksha");
  let session = naksha.NakshaSession.Companion.get();
  let mapi = require("jbon").Jb.map;
  let t = new naksha.PgTrigger(
    TG_OP, TG_NAME, TG_WHEN, TG_LEVEL, TG_RELID, TG_TABLE_NAME, TG_TABLE_SCHEMA,
    mapi.isMap(NEW) ? mapi.asMap(NEW) : null,
    mapi.isMap(OLD) ? mapi.asMap(OLD) : null
  );
  session.triggerBefore(t);
  if (TG_OP == "INSERT" || TG_OP == "UPDATE") {
    return NEW
  }
  return OLD;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION naksha_trigger_after() RETURNS trigger AS $$
  let naksha = require("naksha");
  let session = naksha.NakshaSession.Companion.get();
  let mapi = require("jbon").Jb.map;
  let t = new naksha.PgTrigger(
    TG_OP, TG_NAME, TG_WHEN, TG_LEVEL, TG_RELID, TG_TABLE_NAME, TG_TABLE_SCHEMA,
    mapi.isMap(NEW) ? mapi.asMap(NEW) : null,
    mapi.isMap(OLD) ? mapi.asMap(OLD) : null
  );
  session.triggerAfter(t);
  if (TG_OP == "INSERT" || TG_OP == "UPDATE") {
    return NEW
  }
  return OLD;
$$ LANGUAGE 'plv8' IMMUTABLE;

-- TODO: If needed, create a naksha_write_unordered_features!
-- This function expects that operations are ordered by id to avoid deadlocks
-- CREATED, UPDATED -> return null for tags, feature, geo_type and geo
-- DELETED, PURGED, ERROR -> return all data
CREATE OR REPLACE FUNCTION naksha_write_features(
  collection_id text,
  ops bytea[], -- XyzOp (op, id, uuid)
  features bytea[], -- JbFeature (without XZY namespace)
  geometries_type int2[],
  geometries_bytes bytea[], -- WKB, EWKB, TWKB
  tags bytea[] -- XyzTags
) RETURNS TABLE (op text, id text, xyz bytea, tags bytea, feature bytea, geo_type int2, geo bytea, err_no text, err_msg text) AS $$
  let naksha = require("naksha");
  let session = naksha.NakshaSession.Companion.get();
  session.writeFeatures(collection_id, ops, features, geometries_type, geometries_bytes, tags);
$$ LANGUAGE 'plv8' IMMUTABLE;

-- CREATED, UPDATED -> return null for tags, feature, geo_type and geo
-- DELETED, PURGED, ERROR -> return all data
CREATE OR REPLACE FUNCTION naksha_write_collections(
  ops bytea[], -- XyzOp (op, id, uuid)
  features bytea[], -- JbFeature (without XZY namespace)
  geometries_type int2[],
  geometries_bytes bytea[], -- WKB, EWKB, TWKB
  tags bytea[] -- XyzTags
) RETURNS TABLE (op text, id text, xyz bytea, tags bytea, feature bytea, geo_type int2, geo bytea, err_no text, err_msg text) AS $$
  let naksha = require("naksha");
  let session = naksha.NakshaSession.Companion.get();
  session.writeCollections(ops, features, geometries_type, geometries_bytes, tags);
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION naksha_set_stack_trace(enable bool) RETURNS void AS $$
  require("naksha").Static.PRINT_STACK_TRACES = enable;
$$ LANGUAGE 'plv8' VOLATILE;

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
  return require("naksha").Static.partitionNumber(id);
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION naksha_partition_id(id text) RETURNS text AS $$
  return require("naksha").Static.partitionNameForId(id);
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION naksha_geometry(geo_type int2, geo_bytes bytea) RETURNS geometry AS
$$
BEGIN
  IF geo_type = 1 THEN
    RETURN ST_GeomFromWKB(geo_bytes);
  ELSIF geo_type = 2 THEN
    RETURN ST_GeomFromEWKB(geo_bytes);
  ELSIF geo_type = 3 THEN
    RETURN ST_GeomFromTWKB(geo_bytes);
  ELSE
    RETURN null;
  END IF;
END;
$$ LANGUAGE 'plpgsql' IMMUTABLE;

CREATE OR REPLACE FUNCTION jsonb_to_op(op jsonb) RETURNS bytea AS $$
  let jb = require("jbon");
  let builder = jb.XyzBuilder.Companion.create();
  let opCode = jb.XyzOp.Companion.getOpCode(op["op"]);
  return builder.buildXyzOp(opCode, op["id"], op["uuid"]);
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION json_to_op(op_json text) RETURNS bytea AS $$
  let jb = require("jbon");
  let builder = jb.XyzBuilder.Companion.create();
  let op = jb.Jb.env.parse(op_json);
  let opCode = jb.XyzOp.Companion.getOpCode(op["op"]);
  return builder.buildXyzOp(opCode, op["id"], op["uuid"]);
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION op_to_jsonb(op bytea) RETURNS jsonb AS $$
  let jb = require("jbon");
  let xyzOp = new jb.XyzOp();
  xyzOp.mapBytes(op);
  return xyzOp.toIMap();
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION op_to_json(op bytea) RETURNS text AS $$
  let jb = require("jbon");
  let xyzOp = new jb.XyzOp();
  xyzOp.mapBytes(op);
  return jb.Jb.env.stringify(xyzOp.toIMap());
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_created_at(xyz bytea) RETURNS int8 AS $$
  let jb = require("jbon");
  let xyzNs = new jb.XyzNs();
  xyzNs.mapBytes(xyz);
  return xyzNs.createdAt();
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_updated_at(xyz bytea) RETURNS int8 AS $$
  let jb = require("jbon");
  let xyzNs = new jb.XyzNs();
  xyzNs.mapBytes(xyz);
  return xyzNs.updatedAt();
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_txn(xyz bytea) RETURNS int8 AS $$
  let jb = require("jbon");
  let xyzNs = new jb.XyzNs();
  xyzNs.mapBytes(xyz);
  return xyzNs.txn().value;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_version(xyz bytea) RETURNS int4 AS $$
  let jb = require("jbon");
  let xyzNs = new jb.XyzNs();
  xyzNs.mapBytes(xyz);
  return xyzNs.version();
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_extent(xyz bytea) RETURNS int8 AS $$
  let jb = require("jbon");
  let xyzNs = new jb.XyzNs();
  xyzNs.mapBytes(xyz);
  return xyzNs.extent();
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_author(xyz bytea) RETURNS text AS $$
  let jb = require("jbon");
  let xyzNs = new jb.XyzNs();
  xyzNs.mapBytes(xyz);
  return xyzNs.author();
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_author_ts(xyz bytea) RETURNS int8 AS $$
  let jb = require("jbon");
  let xyzNs = new jb.XyzNs();
  xyzNs.mapBytes(xyz);
  return xyzNs.authorTs();
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_app_id(xyz bytea) RETURNS text AS $$
  let jb = require("jbon");
  let xyzNs = new jb.XyzNs();
  xyzNs.mapBytes(xyz);
  return xyzNs.appId();
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_uuid(xyz bytea) RETURNS text AS $$
  let jb = require("jbon");
  let xyzNs = new jb.XyzNs();
  xyzNs.mapBytes(xyz);
  return xyzNs.uuid();
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_puuid(xyz bytea) RETURNS text AS $$
  let jb = require("jbon");
  let xyzNs = new jb.XyzNs();
  xyzNs.mapBytes(xyz);
  return xyzNs.puuid();
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_action(xyz bytea) RETURNS text AS $$
  let jb = require("jbon");
  let xyzNs = new jb.XyzNs();
  xyzNs.mapBytes(xyz);
  return xyzNs.actionAsString();
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_crid(xyz bytea) RETURNS text AS $$
  let jb = require("jbon");
  let xyzNs = new jb.XyzNs();
  xyzNs.mapBytes(xyz);
  return xyzNs.crid();
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_grid(xyz bytea) RETURNS text AS $$
  let jb = require("jbon");
  let xyzNs = new jb.XyzNs();
  xyzNs.mapBytes(xyz);
  return xyzNs.grid();
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION xyz_to_jsonb(xyz bytea, tags bytea) RETURNS jsonb AS $$
  let naksha = require("naksha");
  let jb = require("jbon");
  let session = naksha.NakshaSession.Companion.get();
  let xyzNs = new jb.XyzNs();
  xyzNs.mapBytes(xyz);
  let xyzTags = new jb.XyzTags();
  xyzTags.mapBytes(tags);
  return xyzNs.toIMap(session.storageId, xyzTags.isMapped()?xyzTags.tagsArray():null);
$$ LANGUAGE 'plv8' IMMUTABLE;

-- query like:
-- where tags_to_jsonb(tags) @? '$.x?(@ starts with "Hello")'
-- where tags_to_jsonb(tags) @? '$.y?(@ >= 500)'
-- where tags_to_jsonb(tags) d @? '$.crid?(@ starts with "234")'
-- where tags_to_jsonb(tags) d @? '$.grid?(@ starts with "234")'
-- See:
-- https://www.postgresql.org/docs/16/functions-json.html#FUNCTIONS-SQLJSON-PATH
-- https://support.smartbear.com/alertsite/docs/monitors/api/endpoint/jsonpath.html
-- https://goessner.net/articles/JsonPath/
CREATE OR REPLACE FUNCTION tags_to_jsonb(tags bytea) RETURNS jsonb AS $$
  if (tags == null) return {};
  let jb = require("jbon");
  let xyzTags = new jb.XyzTags();
  xyzTags.mapBytes(tags);
  return xyzTags.tagsMap();
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jsonb_to_tags(tags_json jsonb) RETURNS bytea AS $$
  // TODO: Fix me!!!
  let jb = require("jbon");
  let builder = jb.XyzBuilder.Companion.create();
  let keys = Object.keys(tags_json);
  let i = 0;
  builder.startTags();
  while (i < keys.length) {
    builder.writeTag()
  }
  let opCode = jb.XyzOp.Companion.getOpCode(op["op"]);
  return builder.buildXyzOp(opCode, op["id"], op["uuid"]);
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION feature_id(feature bytea) RETURNS text AS $$
  let naksha = require("naksha");
  let session = naksha.NakshaSession.Companion.get();
  return session.getFeatureId(feature);
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION feature_type(feature bytea) RETURNS text AS $$
  let naksha = require("naksha");
  let session = naksha.NakshaSession.Companion.get();
  return session.getFeatureType(feature);
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jsonb_to_feature(feature jsonb) RETURNS bytea AS $$
  return require("jbon").JbBuilder.Companion.create(1000).buildFeatureFromMap(feature);
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION json_to_feature(feature text) RETURNS bytea AS $$
  return require("jbon").JbBuilder.Companion.create(1000).buildFeatureFromMap(JSON.parse(feature));
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION feature_to_jsonb(feature bytea) RETURNS jsonb AS $$
  let jb = require("jbon");
  let reader = new jb.JbMapFeature();
  reader.mapBytes(feature);
  let map = reader.root().toIMap();
  map["id"] = reader.id();
  return map;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION feature_to_json(feature bytea) RETURNS text AS $$
  let jb = require("jbon");
  let reader = new jb.JbMapFeature();
  reader.mapBytes(feature);
  let map = reader.root().toIMap();
  map["id"] = reader.id();
  return JSON.stringify(map);
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION row_to_ns(created_at int8, updated_at int8, txn int8, action int2, version int4, author_ts int8,
 uid int4, app_id text, author text, geo_grid text, collection_id text) RETURNS bytea AS $$
  let naksha = require("naksha");
  let session = naksha.NakshaSession.Companion.get();
  let mapi = require("jbon").Jb.map;
  let map = mapi.newMap();
  map["created_at"] = created_at;
  map["updated_at"] = updated_at;
  map["txn"] = txn;
  map["action"] = action;
  map["version"] = version;
  map["author_ts"] = author_ts;
  map["uid"] = uid;
  map["app_id"] = app_id;
  map["author"] = author;
  map["geo_grid"] = geo_grid;
  return session.xyzNsFromRow(collection_id, map)
$$ LANGUAGE 'plv8' IMMUTABLE;
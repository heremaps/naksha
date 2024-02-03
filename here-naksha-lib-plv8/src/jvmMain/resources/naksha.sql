CREATE EXTENSION IF NOT EXISTS plv8;

CREATE OR REPLACE FUNCTION naksha_version() RETURNS int8 LANGUAGE 'plpgsql' IMMUTABLE AS $$ BEGIN
  RETURN ${version};
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
  let jb = require("jbon");
  let env = naksha.Plv8Env.Companion.get();
  let session = new naksha.NakshaSession(new naksha.Plv8Sql(), app_name, stream_id, app_id, author);
  jb.JbSession.Companion.threadLocal.set(session);
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

CREATE OR REPLACE FUNCTION naksha_write_features(
  collection_id text,
  ops text[],
  ids text[],
  uuids text[],
  geometries geometry[],
  features bytea[], -- we leave feature untouched, optionally lz4 compressed (lets make a binary instruction for this)
  xyzs bytea[] -- input expected: tags, crid, uuid
) RETURNS TABLE (op text, id text, uuid text, type text, ptype text, feature bytea, xyz bytea, geometry geometry) AS $$
  let naksha = require("naksha");
  let session = naksha.NakshaSession.get();
  session.writeFeatures(collection_id, ops, ids, uuids, geometries, features, xyzs);
$$ LANGUAGE 'plv8' IMMUTABLE;

-- maybe: create "naksha_collections" and store data there (use some low level code to create standard tables)
--        the thing is: "naksha_collections" need to be a collection by itself
--        other option: just use description to store the feature, but it does not allow binary data!
CREATE OR REPLACE FUNCTION naksha_write_collection(
  ops text[],
  ids text[],
  uuids text[],
  geometries geometry[],
  features bytea[], -- do not lz4 compress, we need to read the properties
  xyzs bytea[] -- input expected: tags, crid, uuid
) RETURNS TABLE (op text, id text, uuid text, type text, ptype text, feature bytea, xyz bytea, geometry geometry) AS $$
  let naksha = require("naksha");
  let session = naksha.NakshaSession.get();
  session.writeFeatures(collection_id, ops, ids, uuids, geometries, features, xyzs);
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

CREATE OR REPLACE FUNCTION naksha_feature_id(feature bytea) RETURNS text AS $$
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION naksha_feature_type(feature bytea) RETURNS text AS $$
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION naksha_feature_ptype(feature bytea) RETURNS text AS $$
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE EXTENSION IF NOT EXISTS plv8;

CREATE OR REPLACE FUNCTION naksha_version() RETURNS int8 LANGUAGE 'plpgsql' IMMUTABLE AS $$ BEGIN
  RETURN ${version};
END $$;

-- Initialize JBON for the current session.
CREATE OR REPLACE FUNCTION naksha_start_session(app_name text, app_id text, author text, stream_id text) RETURNS void AS $$
$$ LANGUAGE 'plv8' IMMUTABLE;

-- Trigger function being added to modify xyz-namespace.
CREATE OR REPLACE FUNCTION naksha_trigger_before() RETURNS trigger AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

-- Trigger function being added to write history and deletion entries.
CREATE OR REPLACE FUNCTION naksha_trigger_after() RETURNS trigger AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION naksha_write_features(
  collection_id text,
  ops text[],
  ids text[],
  uuids text[],
  geometries geometry[],
  features bytea[], -- we leave feature untouched, optionally lz4 compressed (lets make a binary instruction for this)
  xyz bytea[] -- input expected: tags, crid, uuid
) RETURNS TABLE (op text, id text, uuid text, type text, ptype text, feature bytea, xyz bytea, geometry geometry) AS $$
  // plv8.return_next({op: "CREATE", id:"foo", uuid:asdas, type:"", ptype:"", feature:new Int8Array(1), ...});
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
  xyz bytea[] -- input expected: tags, crid, uuid
) RETURNS TABLE (op text, id text, uuid text, type text, ptype text, feature bytea, xyz bytea, geometry geometry) AS $$
  // plv8.return_next({op: "CREATE", id:"foo", uuid:asdas, type:"", ptype:"", feature:new Int8Array(1), ...});
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION naksha_err_no() RETURNS text AS $$
$$ LANGUAGE 'plv8' VOLATILE;

CREATE OR REPLACE FUNCTION naksha_err_msg() RETURNS text AS $$
$$ LANGUAGE 'plv8' VOLATILE;

CREATE OR REPLACE FUNCTION naksha_partition_id(id text) RETURNS text AS $$
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

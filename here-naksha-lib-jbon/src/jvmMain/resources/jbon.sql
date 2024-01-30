CREATE EXTENSION IF NOT EXISTS plv8;

-- Initialize JBON for the current session.
CREATE OR REPLACE FUNCTION jb_init() RETURNS void
AS $$
  plv8.jb_init = function () {
    ${here-naksha-lib-jbon.js}
  }
  plv8.jb_init();
  plv8.jb = plv8["here-naksha-lib-jbon"].com.here.naksha.lib.jbon;
$$ LANGUAGE 'plv8' IMMUTABLE;

-- Access into object using a path like "properties.@ns:com:here:xyz.tags"
-- This allows navigating arrays and maps
-- The dot must be escaped using a double dot, for example "properties.na..me" means properties->name

CREATE OR REPLACE FUNCTION jb_get_type(bin bytea, path text) RETURNS int4 AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_get_bool(bin bytea, path text) RETURNS boolean AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_get_text(bin bytea, path text) RETURNS text AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_get_int4(bin bytea, path text) RETURNS int4 AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_get_int8(bin bytea, path text) RETURNS int8 AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_get_map(bin bytea, path text) RETURNS bytea AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_get_array(bin bytea, path text) RETURNS bytea AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_get_jsonb(bin bytea, path text) RETURNS jsonb AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

-- Access maps.

CREATE OR REPLACE FUNCTION jb_map_length(bin bytea) RETURNS int4 AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_map_contains_key(bin bytea, key text) RETURNS boolean AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_map_contains_key(bin bytea, index int4) RETURNS boolean AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_map_get_jsonb(bin bytea, key text) RETURNS jsonb AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_map_get_jsonb(bin bytea, index int4) RETURNS jsonb AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_map_get_type(bin bytea, key text) RETURNS int4 AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_map_get_type(bin bytea, index int4) RETURNS int4 AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_map_get_bool(bin bytea, key text) RETURNS boolean AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_map_get_bool(bin bytea, index int4) RETURNS boolean AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_map_get_text(bin bytea, key text) RETURNS text AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_map_get_text(bin bytea, index int4) RETURNS text AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_map_get_int4(bin bytea, key text) RETURNS int4 AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_map_get_int4(bin bytea, index int4) RETURNS int4 AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_map_get_int8(bin bytea, key text) RETURNS int8 AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_map_get_int8(bin bytea, index int4) RETURNS int8 AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_map_get_map(bin bytea, key text) RETURNS bytea AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_map_get_map(bin bytea, index int4) RETURNS bytea AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_map_get_array(bin bytea, key text) RETURNS bytea AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_map_get_array(bin bytea, index int4) RETURNS bytea AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

-- Access arrays.

CREATE OR REPLACE FUNCTION jb_arr_length(bin bytea) RETURNS int4 AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_arr_get_jsonb(bin bytea, index int4) RETURNS jsonb AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_arr_get_type(bin bytea, index int4) RETURNS int4 AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_arr_get_bool(bin bytea, index int4) RETURNS boolean AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_arr_get_text(bin bytea, index int4) RETURNS text AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_arr_get_int4(bin bytea, index int4) RETURNS int4 AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_arr_get_int8(bin bytea, index int4) RETURNS int8 AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_arr_get_map(bin bytea, index int4) RETURNS bytea AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_arr_get_array(bin bytea, index int4) RETURNS bytea AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

-- Other methods

CREATE OR REPLACE FUNCTION jb_to_jsonb(bin bytea) RETURNS jsonb AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_from_jsonb(json jsonb) RETURNS bytea AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

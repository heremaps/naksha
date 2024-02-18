CREATE EXTENSION IF NOT EXISTS plv8;

CREATE OR REPLACE FUNCTION jb_get_type(bin bytea, path text) RETURNS int4 AS $$
  var jb = require("jbon");
  var session = jb.JbSession.Companion.get();
  var view = session.newDataView(bin);
  var reader = new jb.JbReader().mapView(view, 0);
  return reader.unitType();
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_get_bool(bin bytea, path text, alternative boolean) RETURNS boolean AS $$
  return require("jbon").JbPath.Companion.getBool(new Uint8Array(bin), path, alternative);
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_get_text(bin bytea, path text, alternative text) RETURNS text AS $$
  return require("jbon").JbPath.Companion.getString(bin, path, alternative);
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_get_double(bin bytea, path text, alternative double precision) RETURNS double precision AS $$
  return require("jbon").JbPath.Companion.getDouble(new Uint8Array(bin), path, alternative);
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION jb_get_int4(bin bytea, path text, alternative int4) RETURNS int4 AS $$
  return require("jbon").JbPath.Companion.getInt32(new Uint8Array(bin), path, alternative);
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

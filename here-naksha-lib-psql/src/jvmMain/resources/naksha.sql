-- noinspection SqlResolveForFile @ routine/"ST_GeomFromTWKB"
-- noinspection SqlResolveForFile @ routine/"ST_GeomFromEWKB"
-- noinspection SqlResolveForFile @ routine/"ST_GeomFromWKB"

-- Read: https://www.postgresql.org/docs/current/sql-createfunction.html
--
-- Note: We need to set search_path on functions that need a specific one, otherwise
--       autovacuum (automatic analyze job needed for statistics) will raise an error,
--       because it executes the functions under a restrictive search_path setting,
--       being just pg_catalog, in fact!
--
-- RETURNS NULL ON NULL INPUT or STRICT
--   indicates that the function always returns null whenever any of its arguments are null.
--   If this parameter is specified, the function is not executed when there are null arguments;
--   instead a null result is assumed automatically.
--
-- PARALLEL UNSAFE indicates that the function can't be executed in parallel mode and the
--   presence of such a function in an SQL statement forces a serial execution plan. This is the default.
-- PARALLEL RESTRICTED indicates that the function can be executed in parallel mode, but
--   the execution is restricted to parallel group leader.
-- PARALLEL SAFE indicates that the function is safe to run in parallel mode without restriction.
SET SESSION search_path TO ${schemaIdent}, topology, hint_plan, public;

CREATE OR REPLACE FUNCTION buf2bytes (in buffers int, in decimals int default 2, out bytes text)
LANGUAGE 'sql'
IMMUTABLE
PARALLEL SAFE
SET search_path FROM CURRENT
AS $$
  with settings as (
    select current_setting('block_size')::numeric as bs
  ), data as (
    select
      buffers::numeric * bs / 1024 as kib,
      floor(log(1024, buffers::numeric * bs / 1024)) + 1 as log,
      bs
    from settings
  ), prep as (
    select
      case
        when log <= 8 then round((kib / 2 ^ (10 * (log - 1)))::numeric, decimals)
        else buffers * bs
      end as value,
      case log -- see https://en.wikipedia.org/wiki/Byte#Multiple-byte_units
        when 1 then 'KiB'
        when 2 then 'MiB'
        when 3 then 'GiB'
        when 4 then 'TiB'
        when 5 then 'PiB'
        when 6 then 'EiB'
        when 7 then 'ZiB'
        when 8 then 'YiB'
        else 'B'
      end as unit
    from data
  )
  select format('%s %s', value, unit)
  from prep;
$$;

CREATE OR REPLACE FUNCTION bytea_concat(a bytea, b bytea) RETURNS bytea
LANGUAGE 'plpgsql'
IMMUTABLE
PARALLEL SAFE
AS $$ BEGIN
  RETURN a || b;
END $$;

CREATE AGGREGATE bytea_agg(bytea) (
    SFUNC = bytea_concat,
    STYPE = bytea,
    INITCOND = ''
);

-- Returns the packed Naksha extension version: 16 bit major, 16 bit minor, 16 bit revision, 8 bit pre-release tag, 8 bit pre-release version.
CREATE OR REPLACE FUNCTION naksha_version() RETURNS int8
LANGUAGE 'plpgsql'
IMMUTABLE
PARALLEL SAFE
AS $$ BEGIN
  RETURN ${version};
END $$;

-- Returns the storage-id of this storage, this is created when the Naksha extension is installed and never changes.
CREATE OR REPLACE FUNCTION naksha_storage_id() RETURNS text
LANGUAGE 'plpgsql'
IMMUTABLE
PARALLEL SAFE
AS $$ BEGIN
  RETURN ${storageIdLiteral};
END $$;

-- Returns the schema of this storage, this is created when the Naksha extension is installed and never changes.
CREATE OR REPLACE FUNCTION naksha_schema() RETURNS text
LANGUAGE 'plpgsql'
IMMUTABLE
PARALLEL SAFE
AS $$ BEGIN
  RETURN ${schemaLiteral};
END $$;

CREATE OR REPLACE FUNCTION naksha_default_schema() RETURNS text
LANGUAGE 'plpgsql'
IMMUTABLE
PARALLEL SAFE
AS $$ BEGIN
  RETURN ${defaultSchemaLiteral};
END $$;

CREATE OR REPLACE FUNCTION naksha_partition_number(id text) RETURNS integer
LANGUAGE 'plpgsql'
IMMUTABLE PARALLEL SAFE STRICT
AS $$
BEGIN
  RETURN get_byte(digest(id,'md5'),0);
END $$;

CREATE OR REPLACE FUNCTION naksha_partition_number(tuple_number bytea) RETURNS integer
LANGUAGE 'plpgsql'
IMMUTABLE PARALLEL SAFE STRICT
AS $$
BEGIN
  RETURN get_byte(tuple_number,7);
END $$;

CREATE OR REPLACE FUNCTION naksha_partition_number(store_number bigint) RETURNS integer
LANGUAGE 'plpgsql'
IMMUTABLE PARALLEL SAFE STRICT
AS $$
BEGIN
  RETURN store_number & 255;
END $$;

CREATE OR REPLACE FUNCTION naksha_jbon_feature_to_json(jbon bytea) RETURNS json
LANGUAGE 'plv8'
IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  if (typeof require !== "function") {
    plv8.find_function("es_modules_init")();
    if (typeof require !== "function") {
      plv8.elog(ERROR, "Failed to initialize module system");
    }
  }
  const { Platform } = require("naksha_base");
  const { JbFeatureDecoder } = require("naksha_jbon");
  let decoder = new JbFeatureDecoder();
  decoder.mapBytes(jbon);
  return Platform.toJSON(decoder.toAnyObject());
$$;

CREATE OR REPLACE FUNCTION naksha_jbon_feature_to_jsonb(jbon bytea) RETURNS jsonb
LANGUAGE 'plpgsql'
IMMUTABLE PARALLEL SAFE STRICT
AS $$ BEGIN
  RETURN naksha_jbon_feature_to_json(jbon)::jsonb;
END $$;

CREATE OR REPLACE FUNCTION naksha_jbon_map_to_json(jbon bytea) RETURNS json
LANGUAGE 'plv8'
IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  if (typeof require !== "function") {
    plv8.find_function("es_modules_init")();
    if (typeof require !== "function") {
      plv8.elog(ERROR, "Failed to initialize module system");
    }
  }
  const { Platform } = require("naksha_base");
  const { JbFeatureDecoder } = require("naksha_jbon");
  let decoder = new JbFeatureDecoder();
  decoder.mapBytes(jbon);
  return Platform.toJSON(decoder.toMap());
$$;

CREATE OR REPLACE FUNCTION naksha_jbon_map_to_jsonb(jbon bytea) RETURNS jsonb
LANGUAGE 'plpgsql'
IMMUTABLE PARALLEL SAFE STRICT
AS $$ BEGIN
  RETURN naksha_jbon_map_to_json(jbon)::jsonb;
END $$;

CREATE OR REPLACE FUNCTION naksha_tags(tags bytea, flags int4) RETURNS jsonb
LANGUAGE 'plpgsql'
IMMUTABLE PARALLEL SAFE STRICT
AS $$
DECLARE
  encoding int4;
  gzip boolean;
BEGIN
  encoding = (flags >> 8) & 15;
  gzip = (encoding & 1) = 1;
  if (gzip) then
    tags = gunzip(tags);
    encoding = encoding & 14;
  end if;
  if (encoding = 0) then -- JBON
    return naksha_jbon_map_to_jsonb(tags);
  elsif (encoding = 2) then -- JSON
    return tags::text::jsonb;
  end if;
  -- Unknown encoding
  return null;
END $$;

CREATE OR REPLACE FUNCTION naksha_feature(feature bytea, flags int4) RETURNS jsonb
LANGUAGE 'plpgsql'
IMMUTABLE PARALLEL SAFE STRICT
AS $$
DECLARE
  encoding int4;
  gzip boolean;
BEGIN
  encoding = (flags >> 4) & 15;
  gzip = (encoding & 1) = 1;
  if (gzip) then
    feature = gunzip(feature);
    encoding = encoding & 14;
  end if;
  if (encoding = 0) then -- JBON
    return naksha_jbon_feature_to_jsonb(feature);
  elsif (encoding = 2) then -- JSON
    return feature::text::jsonb;
  end if;
  -- Unknown encoding
  return null;
END $$;

CREATE OR REPLACE FUNCTION naksha_geometry(geo bytea, flags int) RETURNS geometry
LANGUAGE 'plpgsql'
IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
DECLARE
  encoding int4;
  gzip boolean;
BEGIN
  encoding = flags & 15;
  gzip = (encoding & 1) = 1;
  if (gzip) then
    geo = gunzip(geo);
    encoding = encoding & 14;
  end if;
  if (encoding = 0) then
    RETURN ST_GeomFromTWKB(geo);
  elsif (encoding = 2) then
    RETURN ST_GeomFromWKB(geo);
  elsif (encoding = 4) then
    RETURN ST_GeomFromEWKB(geo);
  elsif (encoding = 6) then
    RETURN ST_GeomFromGeoJSON(geo::text);
  end if;
  -- Unknown encoding
  return null;
END;
$$;

CREATE OR REPLACE FUNCTION naksha_ref_point(ref_point bytea) RETURNS geometry
LANGUAGE 'plpgsql'
IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
BEGIN
  RETURN ST_GeomFromTWKB(ref_point);
END;
$$;

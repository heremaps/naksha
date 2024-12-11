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
SET SESSION search_path TO "naksha~admin", topology, hint_plan, public;

DROP FUNCTION IF EXISTS buf2bytes(in int, in int, out text);
CREATE FUNCTION buf2bytes(in buffers int, in decimals int default 2, out bytes text)
LANGUAGE 'sql'
IMMUTABLE PARALLEL SAFE
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

DROP AGGREGATE IF EXISTS bytea_agg(bytea);
DROP FUNCTION IF EXISTS bytea_concat(bytea, bytea);

CREATE FUNCTION bytea_concat(a bytea, b bytea) RETURNS bytea
LANGUAGE 'plpgsql'
IMMUTABLE PARALLEL SAFE
AS $$ BEGIN
  RETURN a || b;
END $$;
CREATE AGGREGATE bytea_agg(bytea) (
    SFUNC = bytea_concat,
    STYPE = bytea,
    INITCOND = ''
);

DROP FUNCTION IF EXISTS naksha_version();
-- Returns the packed Naksha extension version: 16 bit major, 16 bit minor, 16 bit revision, 8 bit pre-release tag, 8 bit pre-release version.
CREATE FUNCTION naksha_version() RETURNS int8
LANGUAGE 'plpgsql'
IMMUTABLE PARALLEL SAFE
AS $$ BEGIN
  RETURN ${version};
END $$;

DROP FUNCTION IF EXISTS naksha_storage_id();
-- Returns the storage-id of this storage, this is created when the Naksha extension is installed and never changes.
CREATE FUNCTION naksha_storage_id() RETURNS text
LANGUAGE 'plpgsql'
IMMUTABLE PARALLEL SAFE
AS $$ BEGIN
  RETURN ${storageIdLiteral};
END $$;

DROP FUNCTION IF EXISTS naksha_storage_number();
-- Returns the storage-number of this storage, this is created when the Naksha extension is installed and never changes.
CREATE FUNCTION naksha_storage_number() RETURNS bigint
LANGUAGE 'plpgsql'
IMMUTABLE PARALLEL SAFE
AS $$ BEGIN
  RETURN ${storageNumber};
END $$;

DROP FUNCTION IF EXISTS naksha_partition_number(text);
CREATE FUNCTION naksha_partition_number(id text) RETURNS integer
LANGUAGE 'plpgsql'
IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
BEGIN
  RETURN get_byte(digest(id,'md5'),0);
END $$;

DROP FUNCTION IF EXISTS naksha_partition_number(bytea);
CREATE FUNCTION naksha_partition_number(tuple_number bytea) RETURNS integer
LANGUAGE 'plpgsql'
IMMUTABLE PARALLEL SAFE STRICT
AS $$
BEGIN
  RETURN get_byte(tuple_number,7);
END $$;

DROP FUNCTION IF EXISTS naksha_partition_number(bigint);
CREATE FUNCTION naksha_partition_number(store_number bigint) RETURNS integer
LANGUAGE 'plpgsql'
IMMUTABLE PARALLEL SAFE STRICT
AS $$
BEGIN
  RETURN store_number & 255;
END $$;

DROP FUNCTION IF EXISTS naksha_created_at(bigint, bigint);
CREATE FUNCTION naksha_created_at(created_at bigint, updated_at bigint) RETURNS bigint
LANGUAGE 'plpgsql'
IMMUTABLE PARALLEL SAFE
AS $$
BEGIN
  IF created_at IS NOT NULL THEN
    RETURN created_at;
  END IF;
  RETURN updated_at;
END $$;

DROP FUNCTION IF EXISTS naksha_author(text, text);
CREATE FUNCTION naksha_author(author text, app_id text) RETURNS text
LANGUAGE 'plpgsql'
IMMUTABLE PARALLEL SAFE
AS $$
BEGIN
  IF author IS NOT NULL THEN
    RETURN author;
  END IF;
  RETURN app_id;
END $$;

DROP FUNCTION IF EXISTS naksha_author_ts(bigint, bigint);
CREATE FUNCTION naksha_author_ts(author_ts bigint, updated_at bigint) RETURNS bigint
LANGUAGE 'plpgsql'
IMMUTABLE PARALLEL SAFE
AS $$
BEGIN
  IF author_ts IS NOT NULL THEN
    RETURN author_ts;
  END IF;
  RETURN updated_at;
END $$;

DROP FUNCTION IF EXISTS naksha_jbon_feature_to_json(bytea);
CREATE FUNCTION naksha_jbon_feature_to_json(jbon bytea) RETURNS json
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

DROP FUNCTION IF EXISTS naksha_jbon_feature_to_jsonb(bytea);
CREATE FUNCTION naksha_jbon_feature_to_jsonb(jbon bytea) RETURNS jsonb
LANGUAGE 'plpgsql'
IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$ BEGIN
  RETURN naksha_jbon_feature_to_json(jbon)::jsonb;
END $$;

DROP FUNCTION IF EXISTS naksha_jbon_map_to_json(bytea);
CREATE FUNCTION naksha_jbon_map_to_json(jbon bytea) RETURNS json
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

DROP FUNCTION IF EXISTS naksha_jbon_map_to_jsonb(bytea);
CREATE FUNCTION naksha_jbon_map_to_jsonb(jbon bytea) RETURNS jsonb
LANGUAGE 'plpgsql'
IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$ BEGIN
  RETURN naksha_jbon_map_to_json(jbon)::jsonb;
END $$;

DROP FUNCTION IF EXISTS naksha_tags(bytea, int4);
CREATE FUNCTION naksha_tags(tags bytea, flags int4) RETURNS jsonb
LANGUAGE 'plpgsql'
IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
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

DROP FUNCTION IF EXISTS naksha_feature(bytea, int4);
CREATE FUNCTION naksha_feature(feature bytea, flags int4) RETURNS jsonb
LANGUAGE 'plpgsql'
IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
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

DROP FUNCTION IF EXISTS naksha_geometry(bytea, int4);
CREATE FUNCTION naksha_geometry(geo bytea, flags int4) RETURNS geometry
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
    RETURN ST_SetSRID(ST_GeomFromTWKB(geo), 4326);
  elsif (encoding = 2) then
    RETURN ST_GeomFromWKB(geo, 4326);
  elsif (encoding = 4) then
    RETURN ST_GeomFromEWKB(geo);
  elsif (encoding = 6) then
    RETURN ST_SetSRID(ST_GeomFromGeoJSON(convert_from(geo, 'UTF8')), 4326);
  end if;
  -- Unknown encoding
  return null;
END;
$$;

DROP FUNCTION IF EXISTS naksha_2d(bytea, int4);
CREATE FUNCTION naksha_2d(geo bytea, flags int4) RETURNS geometry
LANGUAGE 'plpgsql'
IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
BEGIN
  RETURN ST_Force2D(naksha_geometry(geo,flags));
END;
$$;

DROP FUNCTION IF EXISTS naksha_3d(bytea, int4);
CREATE FUNCTION naksha_3d(geo bytea, flags int4) RETURNS geometry
LANGUAGE 'plpgsql'
IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
BEGIN
  RETURN ST_Force3D(naksha_geometry(geo,flags), 0);
END;
$$;

DROP FUNCTION IF EXISTS naksha_4d(bytea, int4);
CREATE FUNCTION naksha_4d(geo bytea, flags int4) RETURNS geometry
LANGUAGE 'plpgsql'
IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
BEGIN
  RETURN ST_Force4D(naksha_geometry(geo,flags), 0, 0);
END;
$$;

DROP FUNCTION IF EXISTS naksha_ref_point(bytea);
CREATE FUNCTION naksha_ref_point(ref_point bytea) RETURNS geometry
LANGUAGE 'plpgsql'
IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
BEGIN
  RETURN ST_SetSRID(ST_Force2D(ST_GeomFromTWKB(ref_point)), 4326);
END;
$$;

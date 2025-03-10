-- noinspection SqlNoDataSourceInspectionForFile
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

DROP FUNCTION IF EXISTS buf2bytes(in int4, in int4, out text);
CREATE FUNCTION buf2bytes(in buffers int4, in decimals int4 default 2, out bytes text)
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE
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
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE
AS $$
  SELECT a || b
END $$;

CREATE AGGREGATE bytea_agg(bytea) (
    SFUNC = bytea_concat,
    STYPE = bytea,
    INITCOND = ''
);

DROP FUNCTION IF EXISTS int8recv(bytea, int4);
CREATE FUNCTION int8recv(data bytea, pos int4) RETURNS int8
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
AS $$
  SELECT (get_byte(data, pos + 7)::int8) |
         ((get_byte(data, pos + 6)::int8) << 8) |
         ((get_byte(data, pos + 5)::int8) << 16) |
         ((get_byte(data, pos + 4)::int8) << 24) |
         ((get_byte(data, pos + 3)::int8) << 32) |
         ((get_byte(data, pos + 2)::int8) << 40) |
         ((get_byte(data, pos + 1)::int8) << 48) |
         ((get_byte(data, pos)::int8) << 56)
$$;

DROP FUNCTION IF EXISTS int4recv(bytea, int4);
CREATE FUNCTION int4recv(data bytea, pos int4) RETURNS int4
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT AS $$
  SELECT (get_byte(data, pos + 3)::int4) |
         ((get_byte(data, pos + 2)::int4) << 8) |
         ((get_byte(data, pos + 1)::int4) << 16) |
         ((get_byte(data, pos)::int4) << 24);
$$;

DROP FUNCTION IF EXISTS naksha_version();
-- Returns the packed Naksha extension version: 16 bit major, 16 bit minor, 16 bit revision, 8 bit pre-release tag, 8 bit pre-release version.
CREATE FUNCTION naksha_version() RETURNS int8
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE
AS $$
  SELECT ${version}
END $$;

DROP FUNCTION IF EXISTS naksha_storage_id();
-- Returns the storage-id of this storage, this is created when the Naksha extension is installed and never changes.
CREATE FUNCTION naksha_storage_id() RETURNS text
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE
AS $$
  SELECT ${storageIdLiteral}
END $$;

DROP FUNCTION IF EXISTS naksha_storage_number();
-- Returns the storage-number of this storage, this is created when the Naksha extension is installed and never changes.
CREATE FUNCTION naksha_storage_number() RETURNS int8
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE
AS $$
  SELECT ${storageNumber}
END $$;

DROP FUNCTION IF EXISTS naksha_tn_288(int8, int4, int4, int8, int8, int4);
CREATE FUNCTION naksha_tn_288(storage_num int8, map_num int4, col_num int4, feature_num int8, txn int8, uid int4) RETURNS bytea
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
AS $$
  SELECT int8send(storage_num) || int4send(map_num) || int4send(col_num) || int8send(feature_num) || int8send(txn) || int4send(uid)
END $$;

DROP FUNCTION IF EXISTS naksha_tn_224(int4, int4, int8, int8, int4);
CREATE FUNCTION naksha_tn_224(map_num int4, col_num int4, feature_num int8, txn int8, uid int4) RETURNS bytea
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
AS $$
  SELECT int4send(map_num) || int4send(col_num) || int8send(feature_num) || int8send(txn) || int4send(uid)
END $$;

DROP FUNCTION IF EXISTS naksha_tn_192(int4, int8, int8, int4);
CREATE FUNCTION naksha_tn_192(col_num int4, feature_num int8, txn int8, uid int4) RETURNS bytea
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
AS $$
  SELECT int4send(col_num) || int8send(feature_num) || int8send(txn) || int4send(uid)
END $$;

DROP FUNCTION IF EXISTS naksha_tn_160(int8, int8, int4);
CREATE FUNCTION naksha_tn_160(feature_num int8, txn int8, uid int4) RETURNS bytea
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
AS $$
  SELECT int8send(feature_num) || int8send(txn) || int4send(uid)
END $$;

DROP FUNCTION IF EXISTS naksha_tn_96(int8, int4);
CREATE FUNCTION naksha_tn_96(txn int8, uid int4) RETURNS bytea
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
AS $$
  SELECT int8send(txn) || int4send(uid)
END $$;

DROP FUNCTION IF EXISTS naksha_tn_storage_number(bytea);
CREATE FUNCTION naksha_tn_storage_number(tn bytea) RETURNS int8
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT int8recv(tn, length(tn) - 36)
END $$;

DROP FUNCTION IF EXISTS naksha_tn_map_number(bytea);
CREATE FUNCTION naksha_tn_map_number(tn bytea) RETURNS int4
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT int4recv(tn, length(tn) - 28)
END $$;

DROP FUNCTION IF EXISTS naksha_tn_collection_number(bytea);
CREATE FUNCTION naksha_tn_collection_number(tn bytea) RETURNS int
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
    SELECT int4recv(tn, length(tn) - 24)
END $$;

DROP FUNCTION IF EXISTS naksha_tn_feature_number(bytea) CASCADE;
CREATE FUNCTION naksha_tn_feature_number(tn bytea) RETURNS int8
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT int8recv(tn, length(tn) - 20)
END $$;

DROP FUNCTION IF EXISTS naksha_tn_version(bytea);
CREATE FUNCTION naksha_tn_version(tn bytea) RETURNS int8
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT int8recv(tn, length(tn) - 12)
END $$;

DROP FUNCTION IF EXISTS naksha_tn_uid(bytea);
CREATE FUNCTION naksha_tn_uid(tn bytea) RETURNS int4
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT int4recv(tn, length(tn) - 4)
END $$;

DROP FUNCTION IF EXISTS naksha_feature_number(text);
CREATE FUNCTION naksha_feature_number(id text) RETURNS int8
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT int8recv(digest(id,'md5'), 8) | (-9223372036854775807::int8 - 1)::int8
END $$;

DROP FUNCTION IF EXISTS naksha_partition_number(int8);
CREATE FUNCTION naksha_partition_number(feature_number int8) RETURNS int4
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
AS $$
  SELECT (feature_number & 65535)::int4
END $$;

DROP FUNCTION IF EXISTS naksha_partition_number(text);
CREATE FUNCTION naksha_partition_number(id text) RETURNS int4
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT int4recv(digest(id,'md5'), 12) & 65535
END $$;

DROP FUNCTION IF EXISTS naksha_partition_index(int8, int4);
CREATE FUNCTION naksha_partition_index(feature_number int8, parts int4) RETURNS int4
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
AS $$
  SELECT (feature_number & 65535)::int4 % parts
END $$;

DROP FUNCTION IF EXISTS naksha_partition_index(int4, int4);
CREATE FUNCTION naksha_partition_index(partition_number int4, parts int4) RETURNS int4
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
AS $$
  SELECT partition_number % parts
END $$;

DROP FUNCTION IF EXISTS naksha_version_of(int4, int4, int4, int8);
CREATE FUNCTION naksha_version_of(year int4, month int4, day int4, seq int8) RETURNS int8
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT ((year::int8 & 65535::int8) << 41) |
         ((month::int8 & 15::int8) << 37) |
         ((day::int8 & 31::int8) << 32) |
         (seq & 4294967295::int8)
END $$;

DROP FUNCTION IF EXISTS naksha_version_year(int8);
CREATE FUNCTION naksha_version_year(version int8) RETURNS int4
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT (version >> 41)::int4
END $$;

DROP FUNCTION IF EXISTS naksha_version_month(int8);
CREATE FUNCTION naksha_version_month(version int8) RETURNS int4
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT (version >> 37)::int4 & 15
END $$;

DROP FUNCTION IF EXISTS naksha_version_day(int8);
CREATE FUNCTION naksha_version_day(version int8) RETURNS int4
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT (version >> 32)::int4 & 31
END $$;

DROP FUNCTION IF EXISTS naksha_version_seq(int8);
CREATE FUNCTION naksha_version_seq(version int8) RETURNS int8
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT version & (4294967295::int8)
END $$;

DROP FUNCTION IF EXISTS naksha_version_text(int8);
CREATE FUNCTION naksha_version_text(version int8) RETURNS text
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT naksha_version_year(version) || ':'
      || naksha_version_month(version) || ':'
      || naksha_version_day(version) || ':'
      || naksha_version_seq(version)
END $$;

DROP FUNCTION IF EXISTS naksha_alt32(int4);
CREATE FUNCTION naksha_alt32(num int4) RETURNS int4
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
AS $$
  SELECT (num + 1) | -2147483648
END $$;

DROP FUNCTION IF EXISTS naksha_alt64(int8);
CREATE FUNCTION naksha_alt64(num int8) RETURNS int8
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
AS $$
  SELECT ((num + 65536::int8) & (-65536::int8)) | (num & (65535::int8)) | (((-9223372036854775807::int8) - 1::int8)::int8)
END $$;

DROP FUNCTION IF EXISTS naksha_created_at(int8, int8);
CREATE FUNCTION naksha_created_at(created_at int8, updated_at int8) RETURNS int8
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE
AS $$
  SELECT COALESCE(created_at, updated_at)
END $$;

DROP FUNCTION IF EXISTS naksha_author(text, text);
CREATE FUNCTION naksha_author(author text, app_id text) RETURNS text
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE
AS $$
  SELECT COALESCE(author, app_id)
END $$;

DROP FUNCTION IF EXISTS naksha_author_ts(int8, int8);
CREATE FUNCTION naksha_author_ts(author_ts int8, updated_at int8) RETURNS int8
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE
AS $$
  SELECT COALESCE(author_ts, updated_at)
END $$;

DROP FUNCTION IF EXISTS naksha_jbon_feature_to_json(bytea);
CREATE FUNCTION naksha_jbon_feature_to_json(jbon bytea) RETURNS json
LANGUAGE 'plv8' IMMUTABLE PARALLEL SAFE STRICT
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
  return Platform.toJson(decoder.toAnyObject());
$$;

DROP FUNCTION IF EXISTS naksha_jbon_feature_to_jsonb(bytea);
CREATE FUNCTION naksha_jbon_feature_to_jsonb(jbon bytea) RETURNS jsonb
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT naksha_jbon_feature_to_json(jbon)::jsonb
END $$;

DROP FUNCTION IF EXISTS naksha_jbon_map_to_json(bytea);
CREATE FUNCTION naksha_jbon_map_to_json(jbon bytea) RETURNS json
LANGUAGE 'plv8' IMMUTABLE PARALLEL SAFE STRICT
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
  return Platform.toJson(decoder.toMap());
$$;

DROP FUNCTION IF EXISTS naksha_jbon_map_to_jsonb(bytea);
CREATE FUNCTION naksha_jbon_map_to_jsonb(jbon bytea) RETURNS jsonb
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT naksha_jbon_map_to_json(jbon)::jsonb
END $$;

DROP FUNCTION IF EXISTS naksha_tags(bytea, int4);
CREATE FUNCTION naksha_tags(tags bytea, flags int4) RETURNS jsonb
LANGUAGE 'plpgsql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
DECLARE
  encoding int4;
  gzip boolean;
BEGIN
  -- Because the function is strict, the null check is a duplicate, still
  if (tags is null OR length(tags) = 0) then
     return null;
  end if;
  encoding = (flags >> 8) & 15;
  gzip = (encoding & 1) = 1;
  if (gzip) then
    tags = gunzip(tags);
    encoding = encoding & 14;
  end if;
  if (encoding = 0) then -- JBON
    return naksha_jbon_map_to_jsonb(tags);
  elsif (encoding = 2) then -- JSON
    return convert_from(tags, 'utf-8')::jsonb;
  end if;
  -- Unknown encoding
  return null;
END $$;

DROP FUNCTION IF EXISTS naksha_feature(bytea, int4);
CREATE FUNCTION naksha_feature(feature bytea, flags int4) RETURNS jsonb
LANGUAGE 'plpgsql' IMMUTABLE PARALLEL SAFE STRICT
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
LANGUAGE 'plpgsql' IMMUTABLE PARALLEL SAFE STRICT
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
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT ST_Force2D(naksha_geometry(geo,flags))
END;
$$;

DROP FUNCTION IF EXISTS naksha_3d(bytea, int4);
CREATE FUNCTION naksha_3d(geo bytea, flags int4) RETURNS geometry
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT ST_Force3D(naksha_geometry(geo,flags), 0)
END;
$$;

DROP FUNCTION IF EXISTS naksha_4d(bytea, int4);
CREATE FUNCTION naksha_4d(geo bytea, flags int4) RETURNS geometry
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT ST_Force4D(naksha_geometry(geo,flags), 0, 0)
END;
$$;

DROP FUNCTION IF EXISTS naksha_ref_point(bytea);
CREATE FUNCTION naksha_ref_point(ref_point bytea) RETURNS geometry
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT ST_SetSRID(ST_Force2D(ST_GeomFromTWKB(ref_point)), 4326)
END;
$$;

DROP FUNCTION IF EXISTS naksha_flags_action(int4);
CREATE OR REPLACE FUNCTION naksha_flags_action(flags int4) RETURNS int2
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
AS $$
  -- 0=CREATED; 1=UPDATED; 2=DELETED; 3=UNKNOWN
  SELECT (flags >> 16) & 3
END $$;

DROP FUNCTION IF EXISTS naksha_geo_grid_trim_level(int4, int4);
CREATE OR REPLACE FUNCTION naksha_geo_grid_trim_level(geo_grid int4, new_level int4) RETURNS int4
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT geo_grid >> (2 * (15 - LEAST(new_level, 15)))
END $$;

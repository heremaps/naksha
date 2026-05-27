-- noinspection SqlNoDataSourceInspectionForFile
-- noinspection SqlResolveForFile @ routine/"digest"
-- noinspection SqlResolveForFile @ routine/"gzip"
-- noinspection SqlResolveForFile @ routine/"gunzip"
-- noinspection SqlResolveForFile @ routine/"ST_SetSRID"
-- noinspection SqlResolveForFile @ routine/"ST_GeomFromTWKB"
-- noinspection SqlResolveForFile @ routine/"ST_GeomFromEWKB"
-- noinspection SqlResolveForFile @ routine/"ST_GeomFromWKB"
-- noinspection SqlResolveForFile @ routine/"ST_GeomFromGeoJSON"
-- noinspection SqlResolveForFile @ routine/"ST_Force2D"
-- noinspection SqlResolveForFile @ routine/"ST_Force3D"
-- noinspection SqlResolveForFile @ routine/"ST_Force4D"
-- noinspection SqlResolveForFile @ table/"pg_buffercache"
--
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

CREATE OR REPLACE FUNCTION buf2bytes(in buffers int4, in decimals int4 default 2, out bytes text)
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
$$;

CREATE AGGREGATE bytea_agg(bytea) (
    SFUNC = bytea_concat,
    STYPE = bytea,
    INITCOND = ''
);

CREATE OR REPLACE FUNCTION int8recv(data bytea, pos int4) RETURNS int8
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

CREATE OR REPLACE FUNCTION int4recv(data bytea, pos int4) RETURNS int4
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT AS $$
  SELECT (get_byte(data, pos + 3)::int4) |
         ((get_byte(data, pos + 2)::int4) << 8) |
         ((get_byte(data, pos + 1)::int4) << 16) |
         ((get_byte(data, pos)::int4) << 24)
$$;

CREATE OR REPLACE FUNCTION int2recv(data bytea, pos int4) RETURNS int2
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT AS $$
SELECT (get_byte(data, pos + 1)::int2) |
       ((get_byte(data, pos)::int2) << 8)
$$;

-- Returns the packed Naksha extension version: 16 bit major, 16 bit minor, 16 bit revision, 8 bit pre-release tag, 8 bit pre-release version.
CREATE OR REPLACE FUNCTION naksha_version() RETURNS int8
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE
AS $$
  SELECT ${version}
$$;

-- Returns the storage-id of this storage, this is created when the Naksha extension is installed and never changes.
CREATE OR REPLACE FUNCTION naksha_storage_id() RETURNS text
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE
AS $$
  SELECT ${storageIdLiteral}
$$;

-- Returns the storage-number of this storage, this is created when the Naksha extension is installed and never changes.
CREATE OR REPLACE FUNCTION naksha_storage_number() RETURNS int8
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE
AS $$
  SELECT ${storageNumber}
$$;

CREATE OR REPLACE FUNCTION naksha_tn_256(storage_num int8, map_num int4, col_num int4, feature_num int8, txn int8) RETURNS bytea
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
AS $$
  SELECT int8send(storage_num) || int4send(map_num) || int4send(col_num) || int8send(feature_num) || int8send(txn)
$$;

CREATE OR REPLACE FUNCTION naksha_tn_192(map_num int4, col_num int4, feature_num int8, txn int8) RETURNS bytea
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
AS $$
  SELECT int4send(map_num) || int4send(col_num) || int8send(feature_num) || int8send(txn)
$$;

CREATE OR REPLACE FUNCTION naksha_tn_160(col_num int4, feature_num int8, txn int8) RETURNS bytea
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
AS $$
  SELECT int4send(col_num) || int8send(feature_num) || int8send(txn)
$$;

CREATE OR REPLACE FUNCTION naksha_tn_128(feature_num int8, txn int8) RETURNS bytea
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
AS $$
  SELECT int8send(feature_num) || int8send(txn)
$$;

CREATE OR REPLACE FUNCTION naksha_tn_64(txn int8) RETURNS bytea
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
AS $$
  SELECT int8send(txn)
$$;

CREATE OR REPLACE FUNCTION naksha_tn_64(any_tn bytea) RETURNS bytea
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
AS $$
  SELECT substring(any_tn FROM length(any_tn) - 7 FOR 8)
$$;

CREATE OR REPLACE FUNCTION naksha_tn_storage_number(tn bytea) RETURNS int8
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT int8recv(tn, length(tn) - 32)
$$;

CREATE OR REPLACE FUNCTION naksha_tn_map_number(tn bytea) RETURNS int4
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT int4recv(tn, length(tn) - 24)
$$;

CREATE OR REPLACE FUNCTION naksha_tn_collection_number(tn bytea) RETURNS int
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
    SELECT int4recv(tn, length(tn) - 20)
$$;

CREATE OR REPLACE FUNCTION naksha_tn_feature_number(tn bytea) RETURNS int8
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT int8recv(tn, length(tn) - 16)
$$;

CREATE OR REPLACE FUNCTION naksha_tn_partition_number(tn bytea) RETURNS int4
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  -- The partition-number is the same as the lower 16-bit in the feature-number.
  SELECT int2recv(tn, length(tn) - 10)::int4 & 65535
$$;

CREATE OR REPLACE FUNCTION naksha_tn_partition_index(tn bytea, partitions int4) RETURNS int4
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  -- The partition-number is the same as the lower 16-bit in the feature-number.
  SELECT (int2recv(tn, length(tn) - 10)::int4 & 65535) % partitions
$$;

CREATE OR REPLACE FUNCTION naksha_tn_version(tn bytea) RETURNS int8
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT int8recv(tn, length(tn) - 8)
$$;

CREATE OR REPLACE FUNCTION naksha_tn_year(tn bytea) RETURNS int4
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  -- The top 8-bit are reserved and always 0
  -- The next 15-bit are the year
  -- So, we read the 16-bit, shift right by one, then set all top bit to zero,
  --     because PostgresQL does only have arithmetic shift right (>>), but no
  --     logical shift right (>>>)
  SELECT ((int2recv(tn, length(tn) - 7)::int4) >> 1) & 32767
$$;

CREATE OR REPLACE FUNCTION naksha_tn_action(tn bytea) RETURNS int4
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT (int8recv(tn, length(tn) - 8) & 3)::int4
$$;

CREATE OR REPLACE FUNCTION naksha_feature_number(id text) RETURNS int8
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT int8recv(digest(id,'md5'), 8) | (-9223372036854775807::int8 - 1)::int8
$$;

CREATE OR REPLACE FUNCTION naksha_partition_number(feature_number int8) RETURNS int4
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
AS $$
  SELECT (feature_number & 65535)::int4
$$;

CREATE OR REPLACE FUNCTION naksha_partition_number(id text) RETURNS int4
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT ((CASE WHEN id='0' OR (id~'^[1-9][0-9]{0,18}$' AND id::numeric <= 9223372036854775807) THEN id::int8 ELSE int4recv(digest(id,'md5'), 12) END) & 65535)
$$;

CREATE OR REPLACE FUNCTION naksha_partition_index(id text, parts int4) RETURNS int4
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT naksha_partition_number(id) % parts
$$;

CREATE OR REPLACE FUNCTION naksha_partition_index(feature_number int8, parts int4) RETURNS int4
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
AS $$
  SELECT (feature_number & 65535)::int4 % parts
$$;

CREATE OR REPLACE FUNCTION naksha_partition_index(partition_number int4, parts int4) RETURNS int4
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
AS $$
  SELECT partition_number % parts
$$;

CREATE OR REPLACE FUNCTION naksha_version_of(year int4, month int4, day int4, seq int8) RETURNS int8
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT ((year::int8 & 65535::int8) << 41) |
         ((month::int8 & 15::int8) << 37) |
         ((day::int8 & 31::int8) << 32) |
         (seq & 4294967295::int8)
$$;

CREATE OR REPLACE FUNCTION naksha_version_year(version int8) RETURNS int4
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT (version >> 41)::int4
$$;

CREATE OR REPLACE FUNCTION naksha_version_month(version int8) RETURNS int4
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT (version >> 37)::int4 & 15
$$;

CREATE OR REPLACE FUNCTION naksha_version_day(version int8) RETURNS int4
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT (version >> 32)::int4 & 31
$$;

CREATE OR REPLACE FUNCTION naksha_version_seq(version int8) RETURNS int8
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT version & (4294967295::int8)
$$;

CREATE OR REPLACE FUNCTION naksha_version_text(version int8) RETURNS text
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT naksha_version_year(version) || ':'
      || naksha_version_month(version) || ':'
      || naksha_version_day(version) || ':'
      || naksha_version_seq(version)
$$;

CREATE OR REPLACE FUNCTION naksha_alt32(num int4) RETURNS int4
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
AS $$
  SELECT (num + 1) | -2147483648
$$;

CREATE OR REPLACE FUNCTION naksha_alt64(num int8) RETURNS int8
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
AS $$
  SELECT ((num + 65536::int8) & (-65536::int8)) | (num & (65535::int8)) | (((-9223372036854775807::int8) - 1::int8)::int8)
$$;

CREATE OR REPLACE FUNCTION naksha_created_at(created_at int8, updated_at int8) RETURNS int8
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE
AS $$
  SELECT COALESCE(created_at, updated_at)
$$;

CREATE OR REPLACE FUNCTION naksha_author(author text, app_id text) RETURNS text
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE
AS $$
  SELECT COALESCE(author, app_id)
$$;

CREATE OR REPLACE FUNCTION naksha_author_ts(author_ts int8, updated_at int8) RETURNS int8
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE
AS $$
  SELECT COALESCE(author_ts, updated_at)
$$;

CREATE OR REPLACE FUNCTION naksha_jbon_feature_to_json(jbon bytea) RETURNS json
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

CREATE OR REPLACE FUNCTION naksha_jbon_feature_to_jsonb(jbon bytea) RETURNS jsonb
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT naksha_jbon_feature_to_json(jbon)::jsonb
$$;

CREATE OR REPLACE FUNCTION naksha_jbon_map_to_json(jbon bytea) RETURNS json
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

CREATE OR REPLACE FUNCTION naksha_jbon_map_to_jsonb(jbon bytea) RETURNS jsonb
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT naksha_jbon_map_to_json(jbon)::jsonb
$$;

-- Tags are always stored as `JBON_GZIP`.
CREATE OR REPLACE FUNCTION naksha_tags(tags bytea) RETURNS jsonb
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT naksha_jbon_map_to_jsonb(gunzip(tags))
$$;

-- Decodes the binary `feature` payload to JSONB. The encoding (JBON vs JSON, with or without
-- GZIP) is a per-collection setting; pass the collection's encoding `flags` (4-bit `FE` field).
-- Callers will typically pass a hard-coded constant matching `NakshaCollection.defaultFlags`.
CREATE OR REPLACE FUNCTION naksha_feature(feature bytea, flags int4) RETURNS jsonb
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

-- Geometries are always stored as raw `TWKB`.
CREATE OR REPLACE FUNCTION naksha_geometry(geo bytea) RETURNS geometry
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT ST_SetSRID(ST_GeomFromTWKB(geo), 4326)
$$;

CREATE OR REPLACE FUNCTION naksha_2d(geo bytea) RETURNS geometry
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT ST_Force2D(naksha_geometry(geo))
$$;

CREATE OR REPLACE FUNCTION naksha_3d(geo bytea) RETURNS geometry
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT ST_Force3D(naksha_geometry(geo), 0)
$$;

CREATE OR REPLACE FUNCTION naksha_4d(geo bytea) RETURNS geometry
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT ST_Force4D(naksha_geometry(geo), 0, 0)
$$;

CREATE OR REPLACE FUNCTION naksha_ref_point(ref_point bytea) RETURNS geometry
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT ST_SetSRID(ST_Force2D(ST_GeomFromTWKB(ref_point)), 4326)
$$;

-- The action is encoded in the lower two bits of the `version` (txn).
CREATE OR REPLACE FUNCTION naksha_version_action(version int8) RETURNS int2
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
AS $$
  -- 0=CREATED; 1=UPDATED; 2=DELETED; 3=UNKNOWN
  SELECT (version & 3)::int2
$$;

CREATE OR REPLACE FUNCTION naksha_version_action_text(version int8) RETURNS text
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
AS $$
  SELECT CASE (version & 3)::int2
    WHEN 0 THEN 'CREATED'
    WHEN 1 THEN 'UPDATED'
    WHEN 2 THEN 'DELETED'
    ELSE 'UNKNOWN'
  END
$$;

CREATE OR REPLACE FUNCTION naksha_here_tile_trim_level(here_tile int4, new_level int4) RETURNS int4
LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT
SET search_path FROM CURRENT
AS $$
  SELECT here_tile >> (2 * (15 - LEAST(new_level, 15)))
$$;

-- https://www.postgresql.org/docs/current/pgbuffercache.html
CREATE OR REPLACE FUNCTION naksha_db_cache(dbname text) RETURNS TABLE(relname TEXT, relkind TEXT, count INT)
LANGUAGE 'sql' VOLATILE PARALLEL SAFE STRICT
AS $$
    SELECT c.relname, c.relkind, count(*)
    FROM   pg_database AS a, pg_buffercache AS b, pg_class AS c
    WHERE  c.relfilenode = b.relfilenode
      AND b.reldatabase = a.oid
      AND c.oid >= 16384
      AND a.datname = dbname
    GROUP BY 1, 2
    ORDER BY 3 DESC
$$;

-- https://www.postgresql.org/docs/current/view-pg-locks.html
-- https://www.postgresql.org/docs/current/monitoring-stats.html
--
-- [wait_event_type]
-- LWLock 	The server process is waiting for a lightweight lock. Most such locks protect a
--          particular data structure in shared memory. wait_event will contain a name
--          identifying the purpose of the lightweight lock. (Some locks have specific names;
--          others are part of a group of locks each with a similar purpose.)
-- Lock 	The server process is waiting for a heavyweight lock. Heavyweight locks, also known
--          as lock manager locks or simply locks, primarily protect SQL-visible objects such as
--          tables. However, they are also used to ensure mutual exclusion for certain internal
--          operations such as relation extension. wait_event will identify the type of lock awaited;
-- IO 	    The server process is waiting for an I/O operation to complete. wait_event will identify the specific wait point;
-- [wait_event]
-- BufferContent 	Waiting to access a data page in memory.
-- BufferMapping 	Waiting to associate a data block with a buffer in the buffer pool.
CREATE OR REPLACE FUNCTION naksha_db_lwlocks() RETURNS TABLE(pid INT, info JSON, queries JSON)
LANGUAGE 'sql' VOLATILE PARALLEL SAFE STRICT
AS $$
  SELECT
    psa.pid,
    json_agg(DISTINCT pl.relation::regclass) AS info,
    json_agg(DISTINCT psa.query) AS queries
  FROM pg_stat_activity psa
  LEFT JOIN pg_locks pl ON psa.pid = pl.pid
  WHERE psa.wait_event_type = 'LWLock'
    AND psa.wait_event IN ('BufferContent', 'BufferMapping')
  GROUP BY psa.pid
  ORDER BY psa.pid
$$;

-- Creates a partition array
CREATE OR REPLACE FUNCTION naksha_part_array(prefix TEXT, count INT) RETURNS TEXT[]
LANGUAGE 'sql' VOLATILE PARALLEL SAFE STRICT
AS $$
  SELECT ARRAY(SELECT format('%s$p%s', 'topology', LPAD(gs::TEXT, 3, '0')) FROM generate_series(0, count-1) AS gs)
$$;

-- Estimate the features in the collection with that many partitions
CREATE OR REPLACE FUNCTION naksha_estimate_feature_count(collection_id TEXT, partitions INT) RETURNS int8
LANGUAGE 'sql' VOLATILE PARALLEL SAFE STRICT
AS $$
  SELECT sum(reltuples::int8) AS estimate
  FROM pg_class
  WHERE relname = ANY(naksha_part_array(collection_id,partitions));
$$;

-- Disable or enable auto-vacuum for tables.
CREATE OR REPLACE FUNCTION naksha_set_autovacuum(schema text, tables text[], state text) RETURNS void
LANGUAGE 'plpgsql' VOLATILE STRICT
SET search_path FROM CURRENT
AS $$
DECLARE
    tbl text;
    sql text;
BEGIN
    IF state NOT IN ('on', 'off') THEN
        RAISE EXCEPTION 'Invalid autovacuum state: %, expected "on" or "off"', state;
    END IF;

    FOREACH tbl IN ARRAY tables LOOP
        sql := format('ALTER TABLE %I.%I SET (autovacuum_enabled = %s);', schema, tbl, state);
        EXECUTE sql;
    END LOOP;
END;
$$;

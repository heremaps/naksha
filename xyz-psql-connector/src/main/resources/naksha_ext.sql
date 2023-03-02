---------------------------------------------------------------------------------------------------
-- NEW Naksha extensions
---------------------------------------------------------------------------------------------------
-- Links about error handling:
-- https://www.postgresql.org/docs/current/plpgsql-errors-and-messages.html
-- https://www.postgresql.org/docs/current/errcodes-appendix.html
-- https://www.postgresql.org/docs/9.6/plpgsql-control-structures.html#PLPGSQL-ERROR-TRAPPING
--
-- Links about type/string handling:
-- https://www.postgresql.org/docs/current/functions-formatting.html
--
-- Concurrency information:
-- https://www.postgresql.org/docs/current/explicit-locking.html
--
-- Other useful information about PostgesQL
-- https://www.postgresql.org/docs/current/catalog-pg-class.html
-- https://www.postgresql.org/docs/current/catalog-pg-trigger.html
--
-- NOTE: Debugging in DBeaver can be done by adding notices:
--     RAISE NOTICE 'Hello';
-- and then switch to Output tab (Ctrl+Shift+O)
--
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;
CREATE EXTENSION IF NOT EXISTS btree_gist;
-- CREATE EXTENSION pldbgapi;

-- Notice:
-- When service starts, check all connectors and their schemas if they are up-to-date with ext-version
-- When a new connector is created/updated, synchronously check the database and schema and extension
--   - Should we reject connector create/update, if the database is not accessible or anything fails?
--   - I think we make it optionally like: ?failOrError=false
-- After a certain time period, e.g. every 4 hours, check connectors and especially ensure that history tables exist!
-- Clarify: What happens when the space is modified and the "tableName" is changed?
--    First solution: Do not allow tableName change after creation (review later!)
-- TODO: Add versioning!
-- TODO: Add function to create config-schema!
-- TODO: In long term, maintenance thread needs to clear history (drop too old tables!)
-- TODO: For each space, ask to TTL of history (per space!)
-- TODO: Add notifications for new transactions and then in the publisher ID updated, listen to them to avoid polling for new transactions
-- TODO: tomorrow:
--   Use Token API in Wikvaya Proxy!
--   Deploy new Wikvaya and new XYZ-Hub to E2E!
--   Create ADMIN Tokens
--   Fix that owner is set correctly in Token and used by XYZ-Hub
-- TODO: Change namespace from xyz-e2e ... to naksha-e2e ...
-- TODO: Fix space creation in XYZ-Hub to use the naksha extension
-- Ticket: https://confluence.in.here.com/pages/viewpage.action?pageId=1585083118
-- Modify transaction number in simply drawing from a sequence and cache the number in
-- a config variable
-- This will not be persisted when the transactions ends!
-- SELECT SET_CONFIG('naksha.txn', 'some value', true);
-- This is empty at every transaction!
-- SELECT current_setting('xyz_config.naksha_txn', true);


-- We use function to prevent typos when accessing the config schema.
-- Additionally we can change the config schema this way.
-- Note: There can be only one central config schema in every database.
CREATE SCHEMA IF NOT EXISTS xyz_config;

CREATE OR REPLACE FUNCTION xyz_config.naksha_config_db_id() RETURNS int8 LANGUAGE 'plpgsql' IMMUTABLE AS $BODY$
BEGIN
    return 0::int8;
END $BODY$;

-- Returns the version: 16 bit reserved, 16 bit major, 16 bit minor, 16 bit revision
CREATE OR REPLACE FUNCTION xyz_config.naksha_config_version() RETURNS int8 LANGUAGE 'plpgsql' IMMUTABLE AS $BODY$
BEGIN
    --     major           minor           revision
    return 1::int8 << 32 | 0::int8 << 16 | 0::int8;
END $BODY$;

CREATE OR REPLACE FUNCTION xyz_config.naksha_version_of(major int, minor int, revision int) RETURNS int8 LANGUAGE 'plpgsql' IMMUTABLE AS $BODY$
BEGIN
    return ((major::int8 & x'ffff'::int8) << 32)
         | ((minor::int8 & x'ffff'::int8) << 16)
         | (revision::int8 & x'ffff'::int8);
END $BODY$;

CREATE OR REPLACE FUNCTION xyz_config.naksha_version_extract(version int8)
    RETURNS TABLE
            (
                major    int,
                minor    int,
                revision int
            )
    LANGUAGE 'plpgsql' IMMUTABLE
AS
$$
BEGIN
    RETURN QUERY SELECT ((version >> 32) & x'ffff'::int8)::int  as "major",
                        ((version >> 16) & x'ffff'::int8)::int  as "minor",
                        (version & x'ffff'::int8)::int          as "revision";
END;
$$;

CREATE OR REPLACE FUNCTION xyz_config.naksha_version_to_text(version int8) RETURNS text LANGUAGE 'plpgsql' IMMUTABLE AS $BODY$
BEGIN
    return format('%s.%s.%s',
                  (version >> 32) & x'ffff'::int8,
                  (version >> 16) & x'ffff'::int8,
                  (version & x'ffff'::int8));
END $BODY$;

CREATE OR REPLACE FUNCTION xyz_config.naksha_version_parse(version text) RETURNS int8 LANGUAGE 'plpgsql' IMMUTABLE AS $BODY$
DECLARE
    v text[];
BEGIN
    v := string_to_array(version, '.');
    return xyz_config.naksha_version_of(v[1]::int, v[2]::int, v[3]::int);
END $BODY$;

-- Converts the UUID in a byte-array.
CREATE OR REPLACE FUNCTION xyz_config.naksha_uuid_to_bytes(the_uuid uuid)
    RETURNS bytea
    LANGUAGE 'plpgsql' IMMUTABLE
AS
$BODY$
BEGIN
    return DECODE(REPLACE(the_uuid::text, '-', ''), 'hex');
END
$BODY$;

-- Converts the byte-array into a UUID.
CREATE OR REPLACE FUNCTION xyz_config.naksha_uuid_from_bytes(raw_uuid bytea)
    RETURNS uuid
    LANGUAGE 'plpgsql' IMMUTABLE
AS
$BODY$
BEGIN
    RETURN CAST(ENCODE(raw_uuid, 'hex') AS UUID);
END
$BODY$;

-- Create a UUID from the given transaction number and the given identifier.
-- Review here: https://realityripple.com/Tools/UnUUID/
CREATE OR REPLACE FUNCTION xyz_config.naksha_uuid_of(object_id int8, ts timestamptz, type int, db_id int8)
    RETURNS uuid
    LANGUAGE 'plpgsql' IMMUTABLE
AS
$BODY$
DECLARE
    raw_uuid bytea;
    year int8;
    month int8;
    day int8;
    t int8;
BEGIN
    object_id := object_id & x'0fffffffffffffff'::int8; -- 60 bit
    year := (EXTRACT(year FROM ts)::int8 - 2000::int8) & x'00000000000003ff'::int8;
    month := EXTRACT(month FROM ts)::int8;
    day := EXTRACT(day FROM ts)::int8;
    t := type::int8 & x'0000000000000007'::int8; -- 3 bit
    db_id := db_id & x'0000000fffffffff'::int8; -- 40 bit
    raw_uuid := set_byte('\x00000000000000000000000000000000'::bytea, 0, 0);
    -- 48 high bit of object_id in big endian order (6 byte)
    raw_uuid := set_byte(raw_uuid, 0, ((object_id >> 52) & x'ff'::int8)::int);
    raw_uuid := set_byte(raw_uuid, 1, ((object_id >> 44) & x'ff'::int8)::int);
    raw_uuid := set_byte(raw_uuid, 2, ((object_id >> 36) & x'ff'::int8)::int);
    raw_uuid := set_byte(raw_uuid, 3, ((object_id >> 28) & x'ff'::int8)::int);
    raw_uuid := set_byte(raw_uuid, 4, ((object_id >> 20) & x'ff'::int8)::int);
    raw_uuid := set_byte(raw_uuid, 5, ((object_id >> 12) & x'ff'::int8)::int);
    -- 4 bit version (1000), 4 bit of object_id
    raw_uuid := set_byte(raw_uuid, 6, (x'40'::int8 | ((object_id >> 8) & x'0f'::int8))::int);
    -- low 8 bit of object_id
    raw_uuid := set_byte(raw_uuid, 7, (object_id & x'ff'::int8)::int);
    -- 2 bit variant (10), 6 high bit of year
    raw_uuid := set_byte(raw_uuid, 8, (((year >> 4) & x'3f'::int8) | 128)::int);
    -- 4 low bit year, month (has only 4 bit)
    raw_uuid := set_byte(raw_uuid, 9, (((year & x'0f'::int8) << 4) | month)::int);
    -- 5 bit day, 3 bit object type
    raw_uuid := set_byte(raw_uuid, 10, ((day << 3) | t)::int);
    -- 40 bit db_id (5 byte)
    raw_uuid := set_byte(raw_uuid, 11, ((db_id >> 32) & x'ff'::int8)::int);
    raw_uuid := set_byte(raw_uuid, 12, ((db_id >> 24) & x'ff'::int8)::int);
    raw_uuid := set_byte(raw_uuid, 13, ((db_id >> 16) & x'ff'::int8)::int);
    raw_uuid := set_byte(raw_uuid, 14, ((db_id >> 8) & x'ff'::int8)::int);
    raw_uuid := set_byte(raw_uuid, 15, (db_id & x'ff'::int8)::int);
    RETURN CAST(ENCODE(raw_uuid, 'hex') AS UUID);
END
$BODY$;

CREATE OR REPLACE FUNCTION xyz_config.naksha_uuid_object_id(raw_uuid bytea)
    RETURNS int8
    LANGUAGE 'plpgsql' IMMUTABLE
AS
$BODY$
BEGIN
    return ((get_byte(raw_uuid, 0)::int8) << 52)
         | ((get_byte(raw_uuid, 1)::int8) << 44)
         | ((get_byte(raw_uuid, 2)::int8) << 36)
         | ((get_byte(raw_uuid, 3)::int8) << 28)
         | ((get_byte(raw_uuid, 4)::int8) << 20)
         | ((get_byte(raw_uuid, 5)::int8) << 12)
         | ((get_byte(raw_uuid, 6)::int8 & x'0f'::int8) << 8)
         | ((get_byte(raw_uuid, 7)::int8));
END
$BODY$;

CREATE OR REPLACE FUNCTION xyz_config.naksha_uuid_ts(raw_uuid bytea)
    RETURNS timestamptz
    LANGUAGE 'plpgsql' IMMUTABLE
AS
$BODY$
DECLARE
    year int;
    month int;
    day int;
BEGIN
    -- 6 high bit from byte 8 plus 4 low bit from byte 9
    year = 2000 + (((get_byte(raw_uuid, 8) & x'3f'::int) << 4) | (get_byte(raw_uuid, 9) >> 4));
    -- 4 low bit from byte 9
    month = get_byte(raw_uuid, 9) & x'0f'::int;
    -- 5 high bit from byte 10
    day = get_byte(raw_uuid, 10) >> 3;
    return format('%s-%s-%s', year, month, day)::timestamptz;
END
$BODY$;

CREATE OR REPLACE FUNCTION xyz_config.naksha_uuid_type(raw_uuid bytea)
    RETURNS int
    LANGUAGE 'plpgsql' IMMUTABLE
AS
$BODY$
BEGIN
    return get_byte(raw_uuid, 10) & x'07'::int;
END
$BODY$;

CREATE OR REPLACE FUNCTION xyz_config.naksha_uuid_db_id(raw_uuid bytea)
    RETURNS int8
    LANGUAGE 'plpgsql' IMMUTABLE
AS
$BODY$
BEGIN
    return ((get_byte(raw_uuid, 11)::int8) << 32)
         | ((get_byte(raw_uuid, 12)::int8) << 24)
         | ((get_byte(raw_uuid, 13)::int8) << 16)
         | ((get_byte(raw_uuid, 14)::int8) << 8)
         | ((get_byte(raw_uuid, 15)::int8));
END
$BODY$;

-- Fully extracts all values encoded into an UUID.
CREATE OR REPLACE FUNCTION xyz_config.naksha_uuid_extract(the_uuid uuid)
    RETURNS TABLE
            (
                object_id int8,
                year      int,
                month     int,
                day       int,
                type      int,
                db_id     int8
            )
    LANGUAGE 'plpgsql' IMMUTABLE
AS
$BODY$
DECLARE
    raw_uuid bytea;
    object_id int8;
    ts timestamptz;
    type int;
    db_id int8;
    year int;
    month int;
    day int;
BEGIN
    raw_uuid := xyz_config.naksha_uuid_to_bytes(the_uuid);
    object_id := xyz_config.naksha_uuid_object_id(raw_uuid);
    ts := xyz_config.naksha_uuid_ts(raw_uuid);
    year := EXTRACT(year FROM ts);
    month := EXTRACT(month FROM ts);
    day := EXTRACT(day FROM ts);
    type := xyz_config.naksha_uuid_type(raw_uuid);
    db_id := xyz_config.naksha_uuid_db_id(raw_uuid);
    RETURN QUERY SELECT object_id as "object_id",
                        year      as "year",
                        month     as "month",
                        day       as "day",
                        type      as "type",
                        db_id     as "db_id";
END
$BODY$;

CREATE OR REPLACE FUNCTION xyz_config.naksha_uuid_feature_number(i int8, ts timestamptz)
    RETURNS uuid
    LANGUAGE 'plpgsql' IMMUTABLE
AS $$
DECLARE
    TRANSACTION CONSTANT int := 0;
    FEATURE CONSTANT int := 1;
BEGIN
    RETURN xyz_config.naksha_uuid_of(i, ts, FEATURE, xyz_config.naksha_config_db_id());
END;
$$;

CREATE OR REPLACE FUNCTION xyz_config.naksha_uuid_tx_number(txi int8, ts timestamptz)
    RETURNS uuid
    LANGUAGE 'plpgsql' IMMUTABLE
AS $$
DECLARE
    TRANSACTION CONSTANT int := 0;
    FEATURE CONSTANT int := 1;
BEGIN
    RETURN xyz_config.naksha_uuid_of(txi, ts, TRANSACTION, xyz_config.naksha_config_db_id());
END;
$$;

CREATE OR REPLACE FUNCTION xyz_config.naksha_json_id(f jsonb)
    RETURNS text
    LANGUAGE 'plpgsql' IMMUTABLE
AS $BODY$
DECLARE
    id text;
BEGIN
    id = f->>'id'::text;
    IF id IS NULL THEN
        -- 23502
        RAISE EXCEPTION not_null_violation USING MESSAGE = '"id" must not be null';
    END IF;
    RETURN id;
END
$BODY$;

CREATE OR REPLACE FUNCTION xyz_config.naksha_json_version(f jsonb)
    RETURNS int8
    LANGUAGE 'plpgsql' IMMUTABLE
AS $BODY$
BEGIN
    RETURN (f->'properties'->'@ns:com:here:xyz'->>'version')::int8;
EXCEPTION WHEN OTHERS THEN
    RETURN 1::int8;
END
$BODY$;

CREATE OR REPLACE FUNCTION xyz_config.naksha_json_action(f jsonb)
    RETURNS text
    LANGUAGE 'plpgsql' IMMUTABLE
AS $BODY$
BEGIN
    RETURN f->'properties'->'@ns:com:here:xyz'->>'action';
END
$BODY$;

CREATE OR REPLACE FUNCTION xyz_config.naksha_json_uuid(f jsonb)
    RETURNS uuid
    LANGUAGE 'plpgsql' IMMUTABLE
AS $BODY$
BEGIN
    RETURN (f->'properties'->'@ns:com:here:xyz'->>'uuid')::uuid;
END
$BODY$;

CREATE OR REPLACE FUNCTION xyz_config.naksha_json_txn(f jsonb)
    RETURNS uuid
    LANGUAGE 'plpgsql' IMMUTABLE
AS $BODY$
BEGIN
    return (f->'properties'->'@ns:com:here:xyz'->>'txn')::uuid;
END
$BODY$;

CREATE OR REPLACE FUNCTION xyz_config.naksha_json_txn_ts(f jsonb)
    RETURNS timestamptz
    LANGUAGE 'plpgsql' IMMUTABLE
AS $BODY$
DECLARE
    raw_uuid bytea;
BEGIN
    raw_uuid := xyz_config.naksha_uuid_to_bytes((f->'properties'->'@ns:com:here:xyz'->>'uuid')::uuid);
    return xyz_config.naksha_uuid_ts(raw_uuid);
END
$BODY$;

CREATE OR REPLACE FUNCTION xyz_config.naksha_json_txn_object_id(f jsonb)
    RETURNS int8
    LANGUAGE 'plpgsql' IMMUTABLE
AS $BODY$
DECLARE
    raw_uuid bytea;
BEGIN
    raw_uuid := xyz_config.naksha_uuid_to_bytes((f->'properties'->'@ns:com:here:xyz'->>'uuid')::uuid);
    return xyz_config.naksha_uuid_object_id(raw_uuid);
END
$BODY$;

CREATE OR REPLACE FUNCTION xyz_config.naksha_json_txn_db_id(f jsonb)
    RETURNS int8
    LANGUAGE 'plpgsql' IMMUTABLE
AS $BODY$
DECLARE
    raw_uuid bytea;
BEGIN
    raw_uuid := xyz_config.naksha_uuid_to_bytes((f->'properties'->'@ns:com:here:xyz'->>'uuid')::uuid);
    return xyz_config.naksha_uuid_db_id(raw_uuid);
END
$BODY$;

CREATE OR REPLACE FUNCTION xyz_config.naksha_json_puuid(f jsonb)
    RETURNS uuid
    LANGUAGE 'plpgsql' IMMUTABLE
AS $BODY$
BEGIN
    RETURN (f->'properties'->'@ns:com:here:xyz'->>'puuid')::uuid;
END
$BODY$;

CREATE OR REPLACE FUNCTION xyz_config.naksha_json_createdAt(f jsonb)
    RETURNS int8
    LANGUAGE 'plpgsql' IMMUTABLE
AS $BODY$
BEGIN
    RETURN (f->'properties'->'@ns:com:here:xyz'->>'createdAt')::int8;
END
$BODY$;

CREATE OR REPLACE FUNCTION xyz_config.naksha_json_updatedAt(f jsonb)
    RETURNS int8
    LANGUAGE 'plpgsql' IMMUTABLE
AS $BODY$
BEGIN
    RETURN (f->'properties'->'@ns:com:here:xyz'->>'updatedAt')::int8;
END
$BODY$;

CREATE OR REPLACE FUNCTION xyz_config.naksha_json_rtcts(f jsonb)
    RETURNS int8
    LANGUAGE 'plpgsql' IMMUTABLE
AS $BODY$
BEGIN
    RETURN (f->'properties'->'@ns:com:here:xyz'->>'rtcts')::int8;
END
$BODY$;

CREATE OR REPLACE FUNCTION xyz_config.naksha_json_rtuts(f jsonb)
    RETURNS int8
    LANGUAGE 'plpgsql' IMMUTABLE
AS $BODY$
BEGIN
    RETURN (f->'properties'->'@ns:com:here:xyz'->>'rtuts')::int8;
END
$BODY$;

CREATE OR REPLACE FUNCTION xyz_config.naksha_json_lastUpdatedBy(f jsonb)
    RETURNS text
    LANGUAGE 'plpgsql' IMMUTABLE
AS $BODY$
BEGIN
    RETURN f->'properties'->'@ns:com:here:mom:meta'->>'lastUpdatedBy';
END
$BODY$;

-- Returns the current milliseconds (epoch time) form the given timestamp.
CREATE OR REPLACE FUNCTION xyz_config.naksha_ts_millis(ts timestamptz)
    RETURNS int8
    LANGUAGE 'plpgsql' IMMUTABLE
AS $BODY$
BEGIN
    -- Note: "epoch": returns the number of seconds, including fractional parts.
    return (EXTRACT(epoch from ts) * 1000)::int8;
END
$BODY$;

-- Returns the current microseconds (epoch time) form the given timestamp.
CREATE OR REPLACE FUNCTION xyz_config.naksha_ts_micros(ts timestamptz)
    RETURNS int8
    LANGUAGE 'plpgsql' IMMUTABLE
AS $BODY$
BEGIN
    -- Note: "epoch": returns the number of seconds, including fractional parts.
    return (EXTRACT(epoch from ts) * 1000000)::int8;
END
$BODY$;

CREATE OR REPLACE FUNCTION xyz_config.naksha_space_before_trigger()
    RETURNS trigger
    LANGUAGE 'plpgsql' STABLE
AS $BODY$
DECLARE
    new_uuid uuid;
    txn uuid;
    ts_millis int8;
    rts_millis int8;
    i int8;
    xyz jsonb;
BEGIN
    rts_millis := xyz_config.naksha_ts_millis(clock_timestamp());
    ts_millis := xyz_config.naksha_ts_millis(current_timestamp);
    txn := xyz_config.naksha_tx_current();
    i = nextval('"'||TG_TABLE_SCHEMA||'"."'||TG_TABLE_NAME||'_i_seq"');
    new_uuid := xyz_config.naksha_uuid_feature_number(i, current_timestamp);

    IF TG_OP = 'INSERT' THEN
       xyz := NEW.jsondata->'properties'->'@ns:com:here:xyz';
       IF xyz IS NULL THEN
         xyz := '{}'::jsonb;
       END IF;
       xyz := jsonb_set(xyz, '{"action"}', ('"CREATE"')::jsonb, true);
       xyz := jsonb_set(xyz, '{"version"}', '1'::jsonb, true);
       xyz := jsonb_set(xyz, '{"puuid"}', ('null')::jsonb, true);
       xyz := jsonb_set(xyz, '{"uuid"}', ('"'||((new_uuid)::text)||'"')::jsonb, true);
       xyz := jsonb_set(xyz, '{"txn"}', ('"'||((txn)::text)||'"')::jsonb, true);
       xyz := jsonb_set(xyz, '{"createdAt"}', (ts_millis::text)::jsonb, true);
       xyz := jsonb_set(xyz, '{"updatedAt"}', (ts_millis::text)::jsonb, true);
       xyz := jsonb_set(xyz, '{"rtcts"}', (rts_millis::text)::jsonb, true);
       xyz := jsonb_set(xyz, '{"rtuts"}', (rts_millis::text)::jsonb, true);
	   NEW.jsondata = jsonb_set(NEW.jsondata, '{"properties","@ns:com:here:xyz"}', xyz, true);
       NEW.i = i;
  	   return NEW;
    END IF;

    IF TG_OP = 'UPDATE' THEN
       xyz := NEW.jsondata->'properties'->'@ns:com:here:xyz';
       xyz := jsonb_set(xyz, '{"action"}', ('"UPDATE"')::jsonb, true);
       xyz := jsonb_set(xyz, '{"version"}', (''||(xyz_config.naksha_json_version(OLD.jsondata)+1::int8))::jsonb, true);
       xyz := jsonb_set(xyz, '{"puuid"}', OLD.jsondata->'properties'->'@ns:com:here:xyz'->'uuid', true);
       xyz := jsonb_set(xyz, '{"uuid"}', ('"'||((new_uuid)::text)||'"')::jsonb, true);
       xyz := jsonb_set(xyz, '{"txn"}', ('"'||((txn)::text)||'"')::jsonb, true);
       xyz := jsonb_set(xyz, '{"createdAt"}', OLD.jsondata->'properties'->'@ns:com:here:xyz'->'createdAt', true);
       xyz := jsonb_set(xyz, '{"updatedAt"}', (ts_millis::text)::jsonb, true);
       xyz := jsonb_set(xyz, '{"rtcts"}', OLD.jsondata->'properties'->'@ns:com:here:xyz'->'rtcts', true);
       xyz := jsonb_set(xyz, '{"rtuts"}', (rts_millis::text)::jsonb, true);
       NEW.jsondata = jsonb_set(NEW.jsondata, '{"properties","@ns:com:here:xyz"}', xyz, true);
       NEW.i = i;
       return NEW;
    END IF;

    -- DELETE
    RETURN OLD;
END
$BODY$;

CREATE OR REPLACE FUNCTION xyz_config.naksha_space_after_trigger()
    RETURNS trigger
    LANGUAGE 'plpgsql' VOLATILE
AS $BODY$
DECLARE
    new_uuid uuid;
    txn uuid;
    ts_millis int8;
    rts_millis int8;
    i int8;
    xyz jsonb;
BEGIN
    PERFORM xyz_config.naksha_tx_insert(TG_TABLE_SCHEMA, TG_TABLE_NAME);

    IF TG_OP = 'INSERT' OR TG_OP = 'UPDATE' THEN
        EXECUTE format('INSERT INTO %s."%s_hst" (jsondata,geo,i) VALUES(%L,%L,%L)',
            TG_TABLE_SCHEMA, TG_TABLE_NAME,
            NEW.jsondata, NEW.geo, NEW.i);
        RETURN NEW;
    END IF;

    rts_millis := xyz_config.naksha_ts_millis(clock_timestamp());
    ts_millis := xyz_config.naksha_ts_millis(current_timestamp);
    txn := xyz_config.naksha_tx_current();
    i = nextval('"'||TG_TABLE_SCHEMA||'"."'||TG_TABLE_NAME||'_i_seq"');
    new_uuid := xyz_config.naksha_uuid_feature_number(i, current_timestamp);

    -- We do these updates, because in the "after-trigger" we only write into history.
    xyz := OLD.jsondata->'properties'->'@ns:com:here:xyz';
    IF xyz IS NULL THEN
      xyz := '{}'::jsonb;
    END IF;
    xyz := jsonb_set(xyz, '{"action"}', ('"DELETE"')::jsonb, true);
    xyz := jsonb_set(xyz, '{"version"}', (''||(xyz_config.naksha_json_version(OLD.jsondata)+1::int8))::jsonb, true);
    xyz := jsonb_set(xyz, '{"puuid"}', xyz->'uuid', true);
    xyz := jsonb_set(xyz, '{"uuid"}', ('"'||((new_uuid)::text)||'"')::jsonb, true);
    xyz := jsonb_set(xyz, '{"txn"}', ('"'||((txn)::text)||'"')::jsonb, true);
    -- createdAt and rtcts stay what they are
    xyz := jsonb_set(xyz, '{"updatedAt"}', (ts_millis::text)::jsonb, true);
    xyz := jsonb_set(xyz, '{"rtuts"}', (rts_millis::text)::jsonb, true);
    OLD.jsondata = jsonb_set(OLD.jsondata, '{"properties","@ns:com:here:xyz"}', xyz, true);
    OLD.i = i;
    EXECUTE format('INSERT INTO %s."%s_hst" (jsondata,geo,i) VALUES(%L,%L,%L)',
                   TG_TABLE_SCHEMA, TG_TABLE_NAME,
                   OLD.jsondata, OLD.geo, OLD.i);
    RETURN OLD;
END
$BODY$;

-- Ensures that the given schema and table exist as storage location for a space.
CREATE OR REPLACE FUNCTION xyz_config.naksha_space_ensure(_schema text, _table text)
    RETURNS void
    LANGUAGE 'plpgsql' VOLATILE
AS $BODY$
DECLARE
    full_name text;
    before_name text;
    sql text;
BEGIN
    sql := format('CREATE TABLE IF NOT EXISTS %I.%I ('
               || 'jsondata jsonb, '
               || 'geo geometry(GeometryZ, 4326), '
               || 'i int8 PRIMARY KEY NOT NULL)',
        _schema, _table);
    --RAISE NOTICE '%', sql;
    EXECUTE sql;

    sql := format('CREATE SEQUENCE IF NOT EXISTS %I.%I AS int8 OWNED BY %I.i', _schema, format('%s_i_seq', _table), _table);
    --RAISE NOTICE '%', sql;
    EXECUTE sql;

    -- Note: The HEAD table is updated very often, therefore we should not fill the indices too
    --       much to avoid too many page splits. This is as well helpful when doing bulk loads.

    -- Indices with important constrains.
    EXECUTE format('CREATE UNIQUE INDEX IF NOT EXISTS %I ON %I.%I '
                || 'USING btree (xyz_config.naksha_json_id(jsondata) ASC) WITH (fillfactor=50)',
                    format('%s_id_idx', _table), _schema, _table);
    EXECUTE format('CREATE UNIQUE INDEX IF NOT EXISTS %I ON %I.%I '
                || 'USING btree (xyz_config.naksha_json_uuid(jsondata) DESC) WITH (fillfactor=50)',
                    format('%s_uuid_idx', _table), _schema, _table);

    -- Indices that can be delayed in creation.
    EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I.%I '
                 || 'USING gist (geo) WITH (buffering=ON,fillfactor=50)',
                    format('%s_geo_idx', _table), _schema, _table);
    EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I.%I '
                || 'USING btree (xyz_config.naksha_json_txn(jsondata) DESC) WITH (fillfactor=50)',
                   format('%s_txn_idx', _table), _schema, _table);
    EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I.%I '
                || 'USING btree (xyz_config.naksha_json_createdAt(jsondata) DESC) WITH (fillfactor=50)',
                   format('%s_createdAt_idx', _table), _schema, _table);
    EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I.%I '
                || 'USING btree (xyz_config.naksha_json_updatedAt(jsondata) DESC) WITH (fillfactor=50)',
                   format('%s_updatedAt_idx', _table), _schema, _table);
    EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I.%I '
                || 'USING btree (xyz_config.naksha_json_lastUpdatedBy(jsondata) ASC) WITH (fillfactor=50)',
                   format('%s_lastUpdatedBy_idx', _table), _schema, _table);

    -- We need the before trigger to update the XYZ namespace in jsondata.
    before_name := format('%s_before', _table);
    full_name := format('%I.%I', _schema, _table);
    IF NOT EXISTS(SELECT tgname FROM pg_trigger WHERE NOT tgisinternal AND tgrelid = full_name::regclass and tgname = before_name) THEN
        sql := format('CREATE OR REPLACE TRIGGER %I '
                   || 'BEFORE INSERT OR UPDATE ON %I.%I '
                   ||' FOR EACH ROW EXECUTE FUNCTION xyz_config.naksha_space_before_trigger();',
                       before_name, _schema, _table);
        EXECUTE sql;
    END IF;
END
$BODY$;

-- Ensures that the given schema and table exist as storage location for a space, including the
-- needed history tables and partitions.
CREATE OR REPLACE FUNCTION xyz_config.naksha_space_ensure_with_history(_schema text, _table text)
    RETURNS void
    LANGUAGE 'plpgsql' VOLATILE
AS $BODY$
DECLARE
    name text;
    ts timestamptz;
BEGIN
    PERFORM xyz_config.naksha_space_ensure(_schema, _table);
    name := format('%s_hst', _table);
    EXECUTE format('CREATE TABLE IF NOT EXISTS %I.%I '
                || '(jsondata jsonb, geo geometry(GeometryZ, 4326), i int8 NOT NULL) '
                || 'PARTITION BY RANGE (xyz_config.naksha_json_txn_ts(jsondata))',
                   _schema, name);

    -- We rather use the start of the transaction to ensure that every query works.
    --ts = clock_timestamp();
    ts = current_timestamp;
    PERFORM xyz_config.naksha_space_ensure_with_history__partitionForDay(_schema, _table, ts);
    PERFORM xyz_config.naksha_space_ensure_with_history__partitionForDay(_schema, _table, ts + '1 day'::interval);
    PERFORM xyz_config.naksha_space_ensure_with_history__partitionForDay(_schema, _table, ts + '2 day'::interval);
    PERFORM xyz_config.naksha_space_enable_history(_schema, _table);
END
$BODY$;

-- Ensure that the partition for the given day exists.
CREATE OR REPLACE FUNCTION xyz_config.naksha_space_ensure_with_history__partitionForDay(_schema text, _table text, from_ts timestamptz)
    RETURNS void
    LANGUAGE 'plpgsql' VOLATILE
AS $BODY$
DECLARE
    sql text;
    from_day text;
    to_day text;
    to_ts timestamptz;
    hst_name text;
    hst_part_name text;
BEGIN
    from_day := to_char(from_ts, 'YYYY_MM_DD');
    to_ts := from_ts + '1 day'::interval;
    to_day := to_char(to_ts, 'YYYY_MM_DD');
    hst_name := format('%s_hst', _table);
    hst_part_name := format('%s_hst_%s', _table, from_day); -- example: foo_hst_2023_03_01

    sql := format('CREATE TABLE IF NOT EXISTS %I.%I PARTITION OF %I.%I '
               || 'FOR VALUES FROM (%L::timestamptz) TO (%L::timestamptz);',
                  _schema, hst_part_name, _schema, hst_name,
                  from_day, to_day);
    RAISE NOTICE '%', sql;
    EXECUTE sql;

    -- Note: The history table is updated only for one day and then never touched again, therefore
    --       we want to fill the indices as much as we can, even while this may have a bad effect
    --       when doing bulk loads, because we may have more page splits.
    -- Therefore: Disable history for bulk loads by removing the triggers!

    -- Indices with important constrains.
    sql := format('CREATE UNIQUE INDEX IF NOT EXISTS %I ON %I.%I '
               || 'USING btree (xyz_config.naksha_json_uuid(jsondata) DESC) '
               || 'INCLUDE (i) '
               || 'WITH (fillfactor=90) ',
                  format('%s_uuid_idx', hst_part_name), _schema, hst_part_name);
    RAISE NOTICE '%', sql;
    EXECUTE sql;

    -- Indices that can be delayed in creation.
    sql := format('CREATE INDEX IF NOT EXISTS %I ON %I.%I '
                || 'USING btree (xyz_config.naksha_json_id(jsondata) ASC) '
                || 'INCLUDE (i) '
                || 'WITH (fillfactor=90) ',
                  format('%s_id_idx', hst_part_name), _schema, hst_part_name);
    RAISE NOTICE '%', sql;
    EXECUTE sql;

    sql := format('CREATE INDEX IF NOT EXISTS %I ON %I.%I '
               || 'USING gist (geo, xyz_config.naksha_json_id(jsondata), xyz_config.naksha_json_version(jsondata)) '
               || 'INCLUDE (i) '
               || 'WITH (buffering=ON,fillfactor=90) ',
                  format('%s_geo_idx', hst_part_name), _schema, hst_part_name);
    RAISE NOTICE '%', sql;
    EXECUTE sql;

    sql := format('CREATE INDEX IF NOT EXISTS %I ON %I.%I '
               || 'USING btree (xyz_config.naksha_json_txn(jsondata) DESC) '
               || 'INCLUDE (i) '
               || 'WITH (fillfactor=90) ',
                  format('%s_txn_idx', hst_part_name), _schema, hst_part_name);
    RAISE NOTICE '%', sql;
    EXECUTE sql;

    sql := format('CREATE INDEX IF NOT EXISTS %I ON %I.%I '
               || 'USING btree (xyz_config.naksha_json_createdAt(jsondata) DESC) '
               || 'INCLUDE (i) '
               || 'WITH (fillfactor=90) ',
                  format('%s_createdAt_idx', hst_part_name), _schema, hst_part_name);
    RAISE NOTICE '%', sql;
    EXECUTE sql;

    sql := format('CREATE INDEX IF NOT EXISTS %I ON %I.%I '
                || 'USING btree (xyz_config.naksha_json_updatedAt(jsondata) DESC) '
                || 'INCLUDE (i) '
                || 'WITH (fillfactor=90) ',
                  format('%s_updatedAt_idx', hst_part_name), _schema, hst_part_name);
    RAISE NOTICE '%', sql;
    EXECUTE sql;

    sql := format('CREATE INDEX IF NOT EXISTS %I ON %I.%I '
               || 'USING btree (xyz_config.naksha_json_lastUpdatedBy(jsondata) ASC, '
               ||              'xyz_config.naksha_json_id(jsondata) ASC, '
               ||              'xyz_config.naksha_json_version(jsondata) DESC) '
               || 'INCLUDE (i) '
               || 'WITH (fillfactor=90) ',
                  format('%s_lastUpdatedBy_idx', hst_part_name), _schema, hst_part_name);
    RAISE NOTICE '%', sql;
    EXECUTE sql;
END
$BODY$;


-- Enable the history by adding the triggers to the main table.
CREATE OR REPLACE FUNCTION xyz_config.naksha_space_enable_history(_schema text, _table text)
    RETURNS void
    LANGUAGE 'plpgsql' VOLATILE
AS $BODY$
DECLARE
    after_name text;
    full_name text;
    sql text;
BEGIN
    after_name := format('%s_after', _table);
    full_name := format('%I.%I', _schema, _table);
    IF NOT EXISTS(SELECT tgname FROM pg_trigger WHERE NOT tgisinternal AND tgrelid = full_name::regclass and tgname = after_name) THEN
        sql := format('CREATE OR REPLACE TRIGGER %I '
                   || 'AFTER INSERT OR UPDATE OR DELETE ON %I.%I '
                   ||' FOR EACH ROW EXECUTE FUNCTION xyz_config.naksha_space_after_trigger();',
                      after_name, _schema, _table);
        EXECUTE sql;
    END IF;
END
$BODY$;

-- Disable the history by dropping the triggers from the main table.
CREATE OR REPLACE FUNCTION xyz_config.naksha_space_disable_history(_schema text, _table text)
    RETURNS void
    LANGUAGE 'plpgsql' VOLATILE
AS $BODY$
DECLARE
    after_name text;
BEGIN
    after_name = format('%s_after', _table);
    EXECUTE format('DROP TRIGGER IF EXISTS %I ON %I.%I', after_name, _schema, _table);
END
$BODY$;

-- Return the unique transaction number for the current transaction. If no transaction number is
-- yet acquired, it acquire a new one.
CREATE OR REPLACE FUNCTION xyz_config.naksha_tx_current()
    RETURNS uuid
    LANGUAGE 'plpgsql' STABLE
AS $$
DECLARE
    value  text;
    sql    text;
    txi    int8;
    txn    uuid;
BEGIN
    value := current_setting('naksha.txn', true);
    IF coalesce(value, '') <> '' THEN
        -- RAISE NOTICE 'found value = %', value;
        return value::uuid;
    END IF;

    txi := nextval('xyz_config.transactions_txi_seq');
    txn := xyz_config.naksha_uuid_tx_number(txi, current_timestamp);
    sql := format('SELECT SET_CONFIG(%L, %L::text, true)', 'naksha.txn', txn, true);
    -- RAISE NOTICE 'create value via sql = %', sql;
    EXECUTE sql;
    RETURN txn;
END
$$;

CREATE OR REPLACE FUNCTION xyz_config.naksha_tx_ensure()
    RETURNS void
    LANGUAGE 'plpgsql' VOLATILE
AS $BODY$
BEGIN
    EXECUTE 'CREATE TABLE IF NOT EXISTS xyz_config.transactions ('
        || 'i           BIGSERIAL PRIMARY KEY NOT NULL, '
        || 'txid        int8 NOT NULL, '
        || 'txi         int8 NOT NULL, '
        || 'txts        timestamptz NOT NULL, '
        || 'txn         uuid NOT NULL, '
        || '"schema"    text COLLATE "C" NOT NULL, '
        || '"table"     text COLLATE "C" NOT NULL, '
        || 'commit_msg  text COLLATE "C", '
        || 'commit_json jsonb, '
        || 'space       text COLLATE "C", '
        || 'id          int8, '
        || 'ts          timestamptz'
        || ')';

    EXECUTE 'CREATE SEQUENCE IF NOT EXISTS xyz_config.transactions_txi_seq AS int8';
    EXECUTE 'CREATE SEQUENCE IF NOT EXISTS xyz_config.transactions_id_seq AS int8';

    -- unique index: id DESC, schema ASC, table ASC
    EXECUTE 'CREATE UNIQUE INDEX IF NOT EXISTS transactions_id_idx '
         || 'ON xyz_config.transactions USING btree (id DESC, "schema" ASC, "table" ASC)'
         || 'INCLUDE (i)';

    -- index: ts DESC
    EXECUTE 'CREATE INDEX IF NOT EXISTS transactions_ts_idx '
         || 'ON xyz_config.transactions USING btree (ts DESC)'
         || 'INCLUDE (i)';

    -- unique index: txn DESC, schema ASC, table ASC
    EXECUTE 'CREATE UNIQUE INDEX IF NOT EXISTS transactions_txn_idx '
                || 'ON xyz_config.transactions USING btree (txn DESC, "schema" ASC, "table" ASC) '
        || 'INCLUDE (i)';

    -- index: txid DESC, schema ASC, table ASC
    EXECUTE 'CREATE INDEX IF NOT EXISTS transactions_txid_idx '
         || 'ON xyz_config.transactions USING btree (txid DESC, "schema" ASC, "table" ASC)'
         || 'INCLUDE (i)';

    -- index: txts DESC, txn DESC
    EXECUTE 'CREATE INDEX IF NOT EXISTS transactions_txts_idx '
         || 'ON xyz_config.transactions USING btree (txts DESC, txn DESC) '
         || 'INCLUDE (i)';

    -- index: space ASC, txn DESC, schema ASC, table ASC
    EXECUTE 'CREATE INDEX IF NOT EXISTS transactions_space_idx '
         || 'ON xyz_config.transactions USING btree (space ASC, txn DESC, "schema" ASC, "table" ASC) '
         || 'INCLUDE (i)';
END
$BODY$;

CREATE OR REPLACE FUNCTION xyz_config.naksha_tx_insert(_schema text, _table text)
    RETURNS void
    LANGUAGE 'plpgsql' VOLATILE
AS $BODY$
DECLARE
    sql      text;
    txn      uuid;
    raw_uuid bytea;
    txi      int8;
BEGIN
    txn := xyz_config.naksha_tx_current();
    raw_uuid := xyz_config.naksha_uuid_to_bytes(txn);
    txi := xyz_config.naksha_uuid_object_id(raw_uuid);
    sql := format('INSERT INTO xyz_config.transactions (txid, txi, txts, txn, "schema", "table") '
               || 'VALUES (%s, %s, %L::timestamptz, %L, %L, %L) '
               || 'ON CONFLICT DO NOTHING',
                 txid_current(), txi, current_timestamp, txn, _schema, _table);
    EXECUTE sql;
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE '%s, sql = %', SQLERRM, sql;
END
$BODY$;

CREATE OR REPLACE FUNCTION xyz_config.naksha_tx_add_commit_msg(id text, msg text)
    RETURNS void
    LANGUAGE 'plpgsql' VOLATILE
AS $BODY$
BEGIN
    PERFORM xyz_config.naksha_tx_add_commit_msg(id, msg, null);
END
$BODY$;

CREATE OR REPLACE FUNCTION xyz_config.naksha_tx_add_commit_msg(id text, msg text, attachment jsonb)
    RETURNS void
    LANGUAGE 'plpgsql' VOLATILE
AS $BODY$
DECLARE
    sql      text;
    txn      uuid;
    raw_uuid bytea;
    txi      int8;
BEGIN
    txn := xyz_config.naksha_tx_current();
    raw_uuid := xyz_config.naksha_uuid_to_bytes(txn);
    txi := xyz_config.naksha_uuid_object_id(raw_uuid);
    sql := format('INSERT INTO xyz_config.transactions (txid, txi, txts, txn, "schema", "table", commit_msg, commit_json) '
               || 'VALUES (%s, %s, %L::timestamptz, %L, %L, %L, %L, %L::jsonb) '
               || 'ON CONFLICT DO NOTHING',
                  txid_current(), txi, current_timestamp, txn, 'COMMIT_MSG', id, msg, attachment);
    EXECUTE sql;
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE '%, sql = %', SQLERRM, sql;
END
$BODY$;

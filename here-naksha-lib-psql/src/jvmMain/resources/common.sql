CREATE EXTENSION IF NOT EXISTS plv8;
-- Idea based upon: https://rymc.io/blog/2016/a-deep-dive-into-plv8/
-- Execute this SQL code, then all plv8 codes can use es-modules.
--
-- Usage:
-- let lz4 = require("lz4");
-- let x = lz4.compress(bytes);
--
-- Extension can be installed like:
-- insert into es_modules (name, paths, autoload, source) values ('name', array[]::text[], false, '(() => { modules.exports["id"]=... })()')

CREATE SCHEMA IF NOT EXISTS public;
CREATE SCHEMA IF NOT EXISTS topology;
CREATE SCHEMA IF NOT EXISTS ${schema};

-- Set search path and install extension.
SET SESSION search_path TO ${schema}, public, topology;
CREATE EXTENSION IF NOT EXISTS plv8 SCHEMA public;
CREATE EXTENSION IF NOT EXISTS btree_gist SCHEMA public;
CREATE EXTENSION IF NOT EXISTS btree_gin SCHEMA public;
CREATE EXTENSION IF NOT EXISTS postgis SCHEMA public;
CREATE EXTENSION IF NOT EXISTS postgis_topology SCHEMA topology;
do $$ begin
  if exists (select from pg_available_extensions where name='gzip' and installed_version is null) then
  	CREATE EXTENSION gzip SCHEMA public;
  end if;
  if exists (select from pg_available_extensions where name='pg_hint_plan' and installed_version is null) then
    CREATE SCHEMA IF NOT EXISTS hint_plan;
  	CREATE EXTENSION pg_hint_plan SCHEMA hint_plan;
  end if;
end; $$ language 'plpgsql';

-- Update search-path, extension may have changed it!
SET SESSION search_path TO ${schema}, public, topology;
create table if not exists es_modules (name text primary key, paths text[], source text, autoload bool default false);
create index if not exists es_modules_paths_idx on es_modules using gin(paths);
create index if not exists es_modules_autoload_idx on es_modules using btree(autoload);

-- Initializes the es-modules for this session, loads all modules marked as autoload.
-- Note: After having called this function ones, all requires are done lazy!
-- returns true, when initialization work was done, false if it is already initialized.
CREATE OR REPLACE FUNCTION es_modules_init() RETURNS bool AS $$
  ${common.js}
$$ LANGUAGE 'plv8' IMMUTABLE;

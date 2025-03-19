-- If the pgsql-gzip extension is not installed:
-- https://github.com/pramsey/pgsql-gzip
-- Install the methods using a PLV8 implementation
DO $sql$ BEGIN
  IF NOT EXISTS (SELECT name FROM pg_available_extensions WHERE name='gzip' AND installed_version IS NOT NULL) THEN
    EXECUTE $outer$
      CREATE OR REPLACE FUNCTION gzip(uncompressed BYTEA, compression_level INTEGER) RETURNS BYTEA
      LANGUAGE 'plv8' IMMUTABLE PARALLEL SAFE STRICT
      SET search_path FROM CURRENT
      AS $inner$
        if (!globalThis["require"]) plv8.find_function('es_modules_init')();
        return require('pako').gzip(uncompressed, {"level":compression_level});
      $inner$;
    $outer$;

    EXECUTE $outer$
      CREATE OR REPLACE FUNCTION gzip(uncompressed TEXT, compression_level INTEGER) RETURNS BYTEA
      LANGUAGE 'plv8' IMMUTABLE PARALLEL SAFE STRICT
      SET search_path FROM CURRENT
      AS $inner$
        if (!globalThis["require"]) plv8.find_function('es_modules_init')();
        return require('pako').gzip(uncompressed, {"level":compression_level});
      $inner$;
    $outer$;

    EXECUTE $outer$
      CREATE OR REPLACE FUNCTION gzip(uncompressed BYTEA) RETURNS BYTEA
      LANGUAGE 'plv8' IMMUTABLE PARALLEL SAFE STRICT
      SET search_path FROM CURRENT
      AS $inner$
        if (!globalThis["require"]) plv8.find_function('es_modules_init')();
        return require('pako').gzip(uncompressed);
      $inner$;
    $outer$;

    EXECUTE $outer$
      CREATE OR REPLACE FUNCTION gzip(uncompressed TEXT) RETURNS BYTEA
      LANGUAGE 'plv8' IMMUTABLE PARALLEL SAFE STRICT
      SET search_path FROM CURRENT
      AS $inner$
        if (!globalThis["require"]) plv8.find_function('es_modules_init')();
        return require('pako').gzip(uncompressed);
      $inner$;
    $outer$;

    EXECUTE $outer$
      CREATE OR REPLACE FUNCTION gunzip(compressed BYTEA) RETURNS BYTEA
      LANGUAGE 'plv8' IMMUTABLE PARALLEL SAFE STRICT
      SET search_path FROM CURRENT
      AS $inner$
        if (!globalThis["require"]) plv8.find_function('es_modules_init')();
        return require('pako').ungzip(compressed);
      $inner$;
    $outer$;
  END IF;
END; $sql$ LANGUAGE 'plpgsql';

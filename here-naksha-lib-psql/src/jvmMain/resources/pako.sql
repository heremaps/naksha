-- If the pgsql-gzip extension is not installed:
-- https://github.com/pramsey/pgsql-gzip
-- Install the methods using a PLV8 implementation
DO $sql$ BEGIN
  IF NOT EXISTS (SELECT name FROM pg_available_extensions WHERE name='gzip' AND installed_version IS NOT NULL) THEN
    EXECUTE $outer$
      CREATE OR REPLACE FUNCTION gzip(uncompressed BYTEA, compression_level INTEGER) RETURNS BYTEA AS $inner$
        if (!globalThis["require"]) plv8.find_function('es_modules_init')();
        return require('pako').deflate(uncompressed, {"level":compression_level});
      $inner$ LANGUAGE 'plv8' IMMUTABLE;
    $outer$;

    EXECUTE $outer$
      CREATE OR REPLACE FUNCTION gzip(uncompressed TEXT, compression_level INTEGER) RETURNS BYTEA AS $inner$
        if (!globalThis["require"]) plv8.find_function('es_modules_init')();
        return require('pako').deflate(uncompressed, {"level":compression_level});
      $inner$ LANGUAGE 'plv8' IMMUTABLE;
    $outer$;

    EXECUTE $outer$
      CREATE OR REPLACE FUNCTION gunzip(compressed BYTEA) RETURNS BYTEA AS $inner$
        if (!globalThis["require"]) plv8.find_function('es_modules_init')();
        return require('pako').inflate(compressed);
      $inner$ LANGUAGE 'plv8' IMMUTABLE;
    $outer$;
  END IF;
END; $sql$ LANGUAGE 'plpgsql';

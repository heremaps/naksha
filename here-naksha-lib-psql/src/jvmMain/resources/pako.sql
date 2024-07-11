-- If the pgsql-gzip extension is not installed:
-- https://github.com/pramsey/pgsql-gzip
-- Install the methods using a PLV8 implementation
CREATE OR REPLACE FUNCTION gzip(uncompressed BYTEA, compression_level INTEGER) RETURNS BYTEA AS $$
  if (!globalThis["require"]) plv8.find_function('es_modules_init')();
  return require('pako').deflate(uncompressed, {"level":compression_level});
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION gzip(uncompressed TEXT, compression_level INTEGER) RETURNS BYTEA AS $$
  if (!globalThis["require"]) plv8.find_function('es_modules_init')();
  return require('pako').deflate(uncompressed, {"level":compression_level});
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION gunzip(compressed BYTEA) RETURNS BYTEA AS $$
  if (!globalThis["require"]) plv8.find_function('es_modules_init')();
  return require('pako').inflate(compressed);
$$ LANGUAGE 'plv8' IMMUTABLE;

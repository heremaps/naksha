CREATE OR REPLACE FUNCTION lz4_compress(bytes bytea) RETURNS bytea AS $$
  if (!globalThis["require"]) plv8.find_function('es_modules_init')();
  return require('lz4').compress(bytes);
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION lz4_decompress(bytes bytea) RETURNS bytea AS $$
  if (!globalThis["require"]) plv8.find_function('es_modules_init')();
  return require('lz4').decompress(bytes);
$$ LANGUAGE 'plv8' IMMUTABLE;

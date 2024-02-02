CREATE EXTENSION IF NOT EXISTS plv8;

CREATE OR REPLACE FUNCTION lz4_compress(bytes bytea) RETURNS bytea AS $$
  return require('lz4').compress(bytes);
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION lz4_decompress(bytes bytea) RETURNS bytea AS $$
  return require('lz4').decompress(bytes);
$$ LANGUAGE 'plv8' IMMUTABLE;

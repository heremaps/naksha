CREATE EXTENSION IF NOT EXISTS plv8;

DROP FUNCTION IF EXISTS lz4_compress(bytea);
CREATE OR REPLACE FUNCTION lz4_compress(bytes bytea) RETURNS bytea AS $$
  return require('lz4').compress(bytes);
$$ LANGUAGE 'plv8' IMMUTABLE;

DROP FUNCTION IF EXISTS lz4_decompress(bytea);
CREATE OR REPLACE FUNCTION lz4_decompress(bytes bytea) RETURNS bytea AS $$
  return require('lz4').decompress(bytes);
$$ LANGUAGE 'plv8' IMMUTABLE;

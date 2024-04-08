CREATE EXTENSION IF NOT EXISTS plv8;

CREATE OR REPLACE FUNCTION js_beautify(json text, opt jsonb) RETURNS text AS $$
  return require("beautify").js_beautify(json, opt)
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION js_beautify(json text) RETURNS text AS $$
  return require("beautify").js_beautify(json, {"indent_size":2,"space_in_empty_paren":true})
$$ LANGUAGE 'plv8' IMMUTABLE;

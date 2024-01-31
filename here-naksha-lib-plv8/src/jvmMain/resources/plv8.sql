CREATE EXTENSION IF NOT EXISTS plv8;

-- Initialize JBON for the current session.
CREATE OR REPLACE FUNCTION naksha_init() RETURNS void
AS $$
  plv8.naksha_init = function () {
    ${here-naksha-lib-plv8.js}
    // TODO: Initialize jbon native, create initial tables: "naksha_tx", "naksha_collections"
  }
  plv8.naksha_init();
  plv8.naksha = plv8["here-naksha-lib-plv8"].com.here.naksha.lib.plv8;
$$ LANGUAGE 'plv8' IMMUTABLE;

-- Trigger function being added to modify xyz-namespace.
CREATE OR REPLACE FUNCTION naksha_trigger_before() RETURNS trigger AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

-- Trigger function being added to write history and deletion entries.
CREATE OR REPLACE FUNCTION naksha_trigger_after() RETURNS trigger AS $$
  return null;
$$ LANGUAGE 'plv8' IMMUTABLE;

CREATE OR REPLACE FUNCTION naksha_write_features(
  collection_id text,
  ops text[],
  ids text[],
  features bytea[], -- we leave feature untouched, optionally lz4 compressed (lets make a binary instruction for this)
  xyz bytea[], -- input expected: tags, crid, uuid
  geometries geometry[]
) RETURNS TABLE (op text, id text, uuid text, type text, ptype text, feature bytea, xyz bytea, geometry geometry) AS $$
  // plv8.return_next({op: "CREATE", id:"foo", uuid:asdas, type:"", ptype:"", feature:new Int8Array(1), ...});
$$ LANGUAGE 'plv8' IMMUTABLE;

-- maybe: create "naksha_collections" and store data there (use some low level code to create standard tables)
--        the thing is: "naksha_collections" need to be a collection by itself
--        other option: just use description to store the feature, but it does not allow binary data!
CREATE OR REPLACE FUNCTION naksha_write_collection(
  ops text[],
  ids text[],
  features bytea[], -- do not lz4 compress, we need to read the properties
  xyz bytea[], -- input expected: tags, crid, uuid
  geometries geometry[]
) RETURNS TABLE (op text, id text, uuid text, type text, ptype text, feature bytea, xyz bytea, geometry geometry) AS $$
  // plv8.return_next({op: "CREATE", id:"foo", uuid:asdas, type:"", ptype:"", feature:new Int8Array(1), ...});
$$ LANGUAGE 'plv8' IMMUTABLE;

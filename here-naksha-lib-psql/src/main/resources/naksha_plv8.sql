CREATE EXTENSION IF NOT EXISTS plv8;

CREATE or replace FUNCTION naksha_init_v8() returns void AS $BODY$
  plv8.__init = function () {
    ${here-naksha-lib-plv8.js}
  }
  plv8.__init();
  plv8.__lib = plv8["here-naksha-lib-plv8"].com.here.naksha.lib.plv8;
$BODY$ LANGUAGE 'plv8' IMMUTABLE;

CREATE or replace FUNCTION evalToText(command text) returns text AS $BODY$
  return eval(command);
$BODY$ LANGUAGE 'plv8' IMMUTABLE;
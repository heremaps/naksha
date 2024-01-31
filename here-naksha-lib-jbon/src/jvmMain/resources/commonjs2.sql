CREATE EXTENSION IF NOT EXISTS plv8;
-- Idea based upon: https://rymc.io/blog/2016/a-deep-dive-into-plv8/
-- Execute this SQL code, then all plv8 codes can use commonjs2.
-- Usage:
-- let lz4 = require("lz4");
-- let x = lz4.compress(bytes);
-- Extension can be installed like:
-- INSERT INTO commonjs2_modules (module, autoload, source) values ('name', true|false, '(() => { modules.exports["id"]=... })()')

CREATE TABLE IF NOT EXISTS commonjs2_modules (module text primary key, autoload bool default false, source text);

-- Initializes the commonjs2 for this session, loads all modules marked as autoload.
-- Note: After having called this function ones, all requires are done lazy!
CREATE OR REPLACE FUNCTION commonjs2_init() RETURNS bool AS $$
  if ("function" === typeof require) {
    return true;
  }
  let moduleCache = {};
  load = function(key, source) {
      var module = {exports: {}};
      eval("(function(module, exports) {" + source + "; })")(module, module.exports);
      moduleCache[key] = module.exports;
      return module.exports;
  };
  require = function(module) {
      if(moduleCache[module])
          return moduleCache[module];
      var rows = plv8.execute(
          "SELECT source FROM commonjs2_modules WHERE module = $1",
          [module]
      );
      if(rows.length === 0) {
          plv8.elog(ERROR, 'No such module: ' + module);
          return null;
      }
      return load(module, rows[0].source);
  };
  plv8.execute("SELECT module, source FROM commonjs2_modules WHERE autoload = true").forEach((row) => {
      load(row.module, row.source);
  });
  return false;
$$ LANGUAGE 'plv8' IMMUTABLE;

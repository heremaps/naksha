CREATE EXTENSION IF NOT EXISTS plv8;
-- Idea based upon: https://rymc.io/blog/2016/a-deep-dive-into-plv8/
-- Execute this SQL code, then all plv8 codes can use commonjs2.
-- Usage:
-- let lz4 = require("lz4");
-- let x = lz4.compress(bytes);
-- Extension can be installed like:
-- INSERT INTO commonjs2_modules (module, autoload, source) values ('name', true|false, '(() => { modules.exports["id"]=... })()')

CREATE TABLE IF NOT EXISTS commonjs2_modules (module text primary key, source text, autoload bool default false);

-- Initializes the commonjs2 for this session, loads all modules marked as autoload.
-- Note: After having called this function ones, all requires are done lazy!
-- returns true, when initialization work was done, false if it is already initialized.
CREATE OR REPLACE FUNCTION commonjs2_init() RETURNS bool AS $$
  if (typeof require === "function") {
    return false;
  }
  plv8.moduleCache = {};
  load = function(key, source) {
      plv8.elog(INFO, "Load module " + key);
      var module = {exports: {}};
      eval("(function(module, exports) {\n" + source + ";\n})")(module, module.exports);
      plv8.moduleCache[key] = module.exports;
      return plv8.moduleCache[key];
  };
  require = function(module) {
      if(plv8.moduleCache[module]) {
        //plv8.elog(INFO, "Return cached module "+module);
        return plv8.moduleCache[module];
      }
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
  return true;
$$ LANGUAGE 'plv8' IMMUTABLE;

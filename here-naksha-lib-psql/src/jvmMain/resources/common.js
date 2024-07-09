// noinspection ES6ConvertVarToLetConst

(() => {
  if (typeof globalThis["require"] !== "function") {
    function isGlobal(name) {
      return globalThis[name] != null;
    }
    function info(msg, ...args) {
      if (isGlobal("plv8")) plv8.elog(INFO, msg, ...args); else console.log(msg, ...args);
    }
    function error(msg, ...args) {
      if (isGlobal("plv8")) plv8.elog(ERROR, msg, ...args); else console.error(msg, ...args);
    }
    if (!isGlobal("exports")) globalThis.exports = {};
    globalThis.load = function load(key, source) {
      //info("Load module " + key);
      var module = {exports: {}};
      if (isGlobal("js_beautify")) {
        source = js_beautify(source, {"indent_size": 2, "space_in_empty_paren": true});
      }
      eval("(function(module, exports) {\n" + source + ";\n})")(module, module.exports);
      exports[key] = module.exports;
      return exports[key];
    }
    globalThis.require = function require(module) {
      let m = exports[module];
      if (m != null) return m;
      // TODO: What do we do, if we're not in PLV8?
      var rows = plv8.execute(
          "SELECT source FROM commonjs2_modules WHERE module = $1",
          [module]
      );
      if (rows.length === 0) {
        error("No such module: "+module);
        return null;
      }
      return load(module, rows[0].source);
    };
    if (isGlobal("plv8")) {
      // Auto-Load.
      plv8.execute("SELECT module, source FROM commonjs2_modules WHERE autoload = true").forEach((row) => {
        load(row.module, row.source);
      });
    }
    return true;
  }
  return false;
})()

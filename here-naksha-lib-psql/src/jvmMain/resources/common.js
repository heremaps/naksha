// noinspection ES6ConvertVarToLetConst

(() => {
  if (typeof globalThis["require"] !== "function") {
    function isGlobal(name) { return globalThis[name] != null; }
    function info(msg, ...args) { if (isGlobal("plv8")) plv8.elog(INFO, msg, ...args); else console.log(msg, ...args); }
    function error(msg, ...args) { if (isGlobal("plv8")) plv8.elog(ERROR, msg, ...args); else console.error(msg, ...args); }

    if (!isGlobal("exports")) globalThis.exports = {};
    var modules = {};
    var sources = {};

    // Load the module into exports from the given source.
    globalThis.load = function load(name, source) {
      if (exports[name] != null) return exports[name];
      info("Load module " + name);
      var module = {exports: {}};
      ((module, exports) => {
        eval(source);
      })(module, module.exports);
      modules[name] = module;
      exports[name] = module.exports;
      return exports[name];
    };

    function plv8_fetcher(name) {
      info("Fetch source of "+name+" from 'commonjs2_modules' table");
      var rows = plv8.execute(
          "SELECT source FROM commonjs2_modules WHERE module = $1",
          [name]
      );
      if (rows.length === 0) {
        error("No such module: " + name);
        return null;
      }
      return rows[0].source;
    }

    globalThis.sourceOf = function sourceOf(name, fetch, beautify) {
      beautify=beautify===true;
      if (sources[name]!=null) return sources[name];
      var source = typeof fetch==="function" ? fetch(name) : null;
      if (source == null) throw Error("Failed to fetch source of module: "+name);
      if (beautify && isGlobal("js_beautify")) {
        source = js_beautify(source, {"indent_size": 2, "space_in_empty_paren": true});
      }
      sources[name] = source;
      return source;
    }

    globalThis.require = function require(name) {
      let m = exports[name];
      if (m != null) return m;
      return load(name, sourceOf(name, plv8_fetcher));
    };

    if (isGlobal("plv8")) {
      // Auto-Load.
      plv8.execute("SELECT module, source FROM commonjs2_modules WHERE autoload = true").forEach((row) => {
        var name = row.module;
        var source = row.source;
        info("Autoload "+name);
        sources[name] = source;
        load(name, source);
      });
    }
    return true;
  }
  return false;
})()

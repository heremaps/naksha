(() => {
  if (typeof globalThis["require"] !== "function") {
    function wrapSource(source) {
      // Function to transform import statements
      function transformImports(imports, path) {
        const importMap = imports.split(',')
        .map(part => part.trim().split(/\s+as\s+/))
        .map(([name, alias]) => alias)
        .filter(Boolean) // Remove empty entries
        .join(', ');
        return `const { ${importMap} } = require('${path}');`;
      }
      // Function to transform export statements
      function transformExports(exports) {
        const exportMap = exports.replace(/,\s*$/, '').split(',')
        .map(part => part.trim().split(/\s+as\s+/))
        .filter(Boolean) // Remove empty entries
        .map(([name, alias]) => '"'+name+'": '+name)
        .filter(Boolean) // Remove empty entries
        .join(', ');
        exports=`exportAs({${exportMap}});`;
        return exports;
      }

      // Transform import statements
      source = source.replace(/import\s*{\s*([^}]+)\s*}\s*from\s*['"]([^'"]+)['"];/g, (match, imports, path) => {
        return transformImports(imports, path);
      });
      // Transform export statements
      source = source.replace(/export\s*{\s*([^}]+)\s*};/g, (match, exports) => {
        return transformExports(exports);
      });
      return source;
    }
    globalThis.wrapSource = wrapSource;

    function patchSources(source, moduleName) {
      let patchesByModuleName = {
        "kotlin": {
          "var KtList = {getInstance: Companion_getInstance_0};": "var KtList = KtList_0;",
          "var KtMap = {getInstance: Companion_getInstance_1};": "var KtMap = KtMap_0;",
          "var KtMutableList = {getInstance: Companion_getInstance_2};": "var KtMutableList = KtMutableList_0;"
        }
      }
      let patches = patchesByModuleName[moduleName];
      if (!patches) return source;
      info("Apply patches for "+moduleName)
      for (let key in patches) {
        info("Replace '"+key+"' with '"+patches[key]+"'")
        source = source.replace(key, patches[key]);
      }
      return source
    }

    function isGlobal(name) { return globalThis[name] != null; }
    function isPlv8() { return isGlobal("plv8"); }
    function info(msg, ...args) { if (isPlv8()) plv8.elog(INFO, msg, ...args); else console.log(msg, ...args); }
    function error(msg, ...args) { if (isPlv8()) plv8.elog(ERROR, msg, ...args); else console.error(msg, ...args); }

    globalThis.naksha = {
      // module-name -> module-code
      modules: {},
      // matches path to module name, require tries to translate path to module name.
      pathToModuleName: {},
      // matches path to URI, used when loading from web to translate path to URI.
      nameToUri: {}
    };
    let modules = naksha.modules;
    let pathToModuleName = naksha.pathToModuleName;
    let nameToUri = naksha.nameToUri;
    let sources = {};

    function defineModule(name, source) {
      if (modules[name] != null) return modules[name].exports;
      info("Start defining module " + name);
      source = wrapSource(source);
      source = patchSources(source, name)
      modules[name] = {exports:{}};
      (() => {
        var module = modules[name];
        var exports = module.exports;
        var exportAs = function exportAs(map) {
          for (let key in map) {
            exports[key] = map[key];
          }
        }
        eval(source);
      })();
      info("Done, defined module " + name);
      return modules[name].exports;
    }

    function fetchFromPostgres(name) {
      info("Fetch source of "+name+" from 'commonjs2_modules' table");
      let rows = plv8.execute(
          "SELECT source FROM commonjs2_modules WHERE module = $1",
          [name]
      );
      if (rows.length === 0) {
        error("No such module: " + name);
        return null;
      }
      let source = rows[0].source;
      source += "\n//# sourceURL=" + name + "\n";
      return source
    }

    function fetchFromUri(uri) {
      const xhr = new XMLHttpRequest();
      xhr.open('GET', uri, false);
      xhr.send();
      if (xhr.status === 200) {
        let source = xhr.responseText;
        source = source.replace(/^\/\/# sourceMappingURL=.*\r?\n?/gm, '');
        source += "\n//# sourceURL=" + uri + "\n";
        return source;
      } else {
        throw new Error(`Failed to fetch ${path}: ${xhr.statusText}`);
      }
    }

    function loadSource(name, beautify) {
      beautify=beautify===true;
      if (sources[name]!=null) return sources[name];
      let uri = null;
      let source;
      if (isPlv8()) {
        source = fetchFromPostgres(name)
      } else {
        uri = nameToUri[name];
        if (uri == null) throw Error("Failed to get URI of module "+name+" into URI");
        source = fetchFromUri(uri);
      }
      if (source == null) throw Error("Failed to load source of module: "+name+", uri:"+uri);
      if (beautify && isGlobal("js_beautify")) {
        source = js_beautify(source, {"indent_size": 2, "space_in_empty_paren": true});
      }
      sources[name] = source;
      return source;
    }

    function require(nameOrPath, beautify) {
      let name = pathToModuleName[nameOrPath]||nameOrPath;
      let path = nameOrPath;
      let module = modules[name];
      if (module != null) return module.exports;
      return defineModule(name, loadSource(name, path, beautify));
    }
    globalThis.require = require;

    if (isGlobal("plv8")) {
      // Auto-Load.
      plv8.execute("SELECT module, source FROM commonjs2_modules WHERE autoload = true").forEach((row) => {
        let name = row.module;
        let source = row.source;
        info("Autoload "+name);
        sources[name] = source;
        defineModule(name, source);
      });
    }
    return true;
  }
  return false;
})();

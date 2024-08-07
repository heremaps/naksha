(() => {
  if (typeof globalThis["require"] !== "function") {
    function wrapSource(source, moduleName) {
      let patchedImport = false
      let patchedExport = false

      // Function to transform import statements
      function transformImports(imports, path) {
        // noinspection JSUnusedLocalSymbols
        let importMap = imports.split(',')
        .map(part => part.trim().split(/\s+as\s+/))
        .map(([name, alias]) => alias)
        .filter(Boolean) // Remove empty entries
        .join(', ');
        // TODO: KtCompiler
        if (!patchedImport
            && moduleName === 'naksha_psql'
            && path === "./kotlin-kotlin-stdlib.mjs"
            && importMap.indexOf("copyOf") < 0
        ) {
          info("Apply patch for naksha_psql, import 'copyOf' from kotlin standard library")
          importMap += ", copyOf"
          patchedImport = true
        }
        return `const { ${importMap} } = require('${path}');`;
      }

      // Function to transform export statements
      function transformExports(exports) {
        // noinspection JSUnusedLocalSymbols
        let exportMap = exports.replace(/,\s*$/, '').split(',')
        .map(part => part.trim().split(/\s+as\s+/))
        .filter(Boolean) // Remove empty entries
        .map(([name, alias]) => '"' + name + '": ' + name)
        .filter(Boolean) // Remove empty entries
        .join(', ');
        // TODO: KtCompiler
        if (!patchedExport
            && moduleName === 'kotlin'
            && exportMap.indexOf("copyOf") < 0
        ) {
          info("Apply patch for kotlin standard library, export 'copyOf'")
          exportMap += ', "copyOf": copyOf'
          patchedExport = true
        }
        exports = `exportAs({${exportMap}});`;
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

    // TODO: KtCompiler
    function patchSources(source, moduleName) {
      let patchesByModuleName = {
        "kotlin": {
          "var KtList = {getInstance: Companion_getInstance_0};": "var KtList = KtList_0;",
          "var KtMap = {getInstance: Companion_getInstance_1};": "var KtMap = KtMap_0;",
          "var KtMutableList = {getInstance: Companion_getInstance_2};": "var KtMutableList = KtMutableList_0;",
          "var KtMutableMap = {getInstance: Companion_getInstance_3};": "var KtMutableMap = KtMutableMap_0;"
        }
      }
      let patches = patchesByModuleName[moduleName];
      if (!patches) {
        return source;
      }
      info("Apply patches for " + moduleName)
      for (let key in patches) {
        info("Replace '" + key + "' with '" + patches[key] + "'")
        source = source.replace(key, patches[key]);
      }
      return source
    }

    function isGlobal(name) {
      return globalThis[name] != null;
    }

    function isPlv8() {
      return isGlobal("plv8");
    }

    function info(msg, ...args) {
      if (isPlv8()) {
        plv8.elog(INFO, msg, ...args);
      } else {
        console.log(msg, ...args);
      }
    }

    function error(msg, ...args) {
      if (isPlv8()) {
        plv8.elog(ERROR, msg, ...args);
      } else {
        console.error(msg, ...args);
      }
    }

    globalThis.naksha = {
      // module cache, module-name -> module-code
      modules: {},
      // matches require to module name
      toName: {},
      // matches require URI
      toUri: {}
    };
    let modules = naksha.modules;
    let toName = naksha.toName;
    let toUri = naksha.toUri;
    let sources = {};

    function defineModule(name, source) {
      if (modules[name] != null) {
        return modules[name].exports;
      }
      info("Start defining module " + name);
      source = wrapSource(source, name);
      source = patchSources(source, name)
      modules[name] = {exports: {}};
      (() => {
        // Note: We need to declare these variables via "var" here to allow loading of commonjs modules!
        // noinspection ES6ConvertVarToLetConst
        var module = modules[name];
        // noinspection ES6ConvertVarToLetConst
        var exports = module.exports;
        // noinspection ES6ConvertVarToLetConst,JSUnusedLocalSymbols
        var exportAs = function exportAs(map) {
          for (let key in map) {
            module.exports[key] = map[key];
          }
        }
        // Note: The source may replace module.exports with a different object!
        eval(source);
      })();
      info("Done, defined module " + name);
      return modules[name].exports;
    }

    function getSourceFromRow(row) {
      let name = row.name;
      let paths = row.paths;
      let source = row.source;
      if (Array.isArray(paths)) {
        for (let path in paths) {
          toName[path] = name;
        }
      }
      source += "\n//# sourceURL=" + row.name + "\n";
      return source
    }

    function fetchFromPostgres(id) {
      info("Fetch module " + id + " from 'es_modules' table");
      // noinspection SqlDialectInspection
      let rows = plv8.execute(
          "SELECT name, paths, source FROM es_modules WHERE name=$1 or $1=ANY(paths)",
          [id]
      );
      if (rows.length === 0) {
        error("No such module: " + id);
        return null;
      }
      let row = rows[0]
      let source = getSourceFromRow(row)
      if (id !== row.name) {
        toName[id] = row.name;
        info("Loaded module '" + row.name + " for id '" + id + "'")
      }
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

    function loadSource(id, beautify) {
      beautify = beautify === true;
      if (sources[id] != null) {
        return sources[id];
      }
      let name = toName[id] || id
      if (sources[name] != null) {
        return sources[name];
      }
      let uri = null;
      let source;
      if (isPlv8()) {
        source = fetchFromPostgres(id)
        // Translate id to name again, because the fetchFromPostgres will register aliases!
        name = toName[id] || id
      } else {
        uri = toUri[id];
        if (uri == null) {
          uri = toUri[name]
        }
        if (uri == null) {
          throw Error("Failed to get URI of module '" + id + "', register first in naksha. into URI");
        }
        source = fetchFromUri(uri);
      }
      if (source == null) {
        throw Error("Failed to load source of module: " + id + ", uri:" + uri);
      }
      if (beautify && isGlobal("js_beautify")) {
        source = js_beautify(source, {"indent_size": 2, "space_in_empty_paren": true});
      }
      sources[name] = source;
      return source;
    }

    function require(name, beautify) {
      name = toName[name] || name
      let module = modules[name];
      if (module != null) {
        return module.exports;
      }
      let source = loadSource(name, beautify)
      name = toName[name] || name;
      return defineModule(name, source);
    }

    globalThis.require = require;

    // Auto load modules on plv8.
    if (isGlobal("plv8")) {
      // noinspection SqlDialectInspection
      plv8.execute("SELECT name, paths, source FROM es_modules WHERE autoload = true").forEach((row) => {
        let source = getSourceFromRow(row);
        let name = row.name;
        info("Auto loaded module " + name);
        sources[name] = source;
        defineModule(name, source);
      });
    }
    return true;
  }
  return false;
})();

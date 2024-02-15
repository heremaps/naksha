@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8;

import kotlin.js.json

@JsExport
class Plv8Table : ITable {
    override fun returnNext(vararg pairs: Pair<String, Any?>) {
        val json = json(*pairs)
        js("plv8.return_next(json)")
    }
}
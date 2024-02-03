@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8;

@JsExport
class Plv8Table : ITable {
    override fun returnNext(row: Any) {
        js("return_next(row)")
    }
}
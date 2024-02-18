@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8;

import com.here.naksha.lib.jbon.IMap

@JsExport
class JsPlv8Table : ITable {
    override fun returnNext(row: IMap) {
        js("return_next(row)")
    }
}
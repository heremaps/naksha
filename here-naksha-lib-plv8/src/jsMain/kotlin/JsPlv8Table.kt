@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8;

import com.here.naksha.lib.jbon.IMap

@JsExport
class JsPlv8Table : ITable {

    override fun returnNext(ret: IMap) {
        js("plv8.return_next(row)")
    }
}
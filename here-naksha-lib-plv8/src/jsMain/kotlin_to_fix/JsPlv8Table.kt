@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8;

import naksha.base.P_Map


@JsExport
class JsPlv8Table : naksha.psql.ITable {

    override fun returnNext(row: P_Map<String, Any>) {
        js("plv8.return_next(row)")
    }
}
@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8;

import naksha.jbon.ILog

@JsExport
class JsPlv8Log : ILog {
    override fun info(msg: String, vararg args: Any) {
        js("plv8.elog(INFO, msg, args)")
    }

    override fun warn(msg: String, vararg args: Any) {
        js("plv8.elog(WARN, msg, args)")
    }

    override fun error(msg: String, vararg args: Any) {
        js("plv8.elog(ERROR, msg, args)")
    }
}
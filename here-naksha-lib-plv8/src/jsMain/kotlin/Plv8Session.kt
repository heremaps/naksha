@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8;

import com.here.naksha.lib.jbon.INativeLog
import com.here.naksha.lib.jbon.ISql
import com.here.naksha.lib.jbon.JsSession

/**
 * Special JS session that is optimized for PLV8 (Postgres extension).
 */
@Suppress("unused")
@JsExport
class Plv8Session : JsSession() {
    companion object {
        private val plv8Log = Plv8Log()
        private val plv8Sql = Plv8Sql()

        fun register() {
            register(Plv8Session())
        }
    }

    override fun log(): INativeLog {
        return plv8Log
    }

    override fun sql(): ISql {
        return plv8Sql
    }

}
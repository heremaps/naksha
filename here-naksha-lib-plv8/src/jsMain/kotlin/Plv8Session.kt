@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8;

import com.here.naksha.lib.jbon.JsSession

/**
 * Special JS session that is optimized for PLV8 (Postgres extension).
 */
@JsExport
class Plv8Session : JsSession() {
    companion object {
        fun register() {
            register(Plv8Session())
        }
    }


}
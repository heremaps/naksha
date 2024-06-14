@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.auth.check

import naksha.base.P_AnyList
import kotlin.js.JsExport

/**
 * A test operation.
 */
@JsExport
abstract class CompiledCheck: P_AnyList() {
    abstract fun matches(value: Any?): Boolean
}
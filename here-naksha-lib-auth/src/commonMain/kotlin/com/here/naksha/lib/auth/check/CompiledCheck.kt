@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.auth.check

import naksha.base.AnyListProxy
import kotlin.js.JsExport

/**
 * A test operation.
 */
@JsExport
abstract class CompiledCheck: AnyListProxy() {
    abstract fun matches(value: Any?): Boolean
}
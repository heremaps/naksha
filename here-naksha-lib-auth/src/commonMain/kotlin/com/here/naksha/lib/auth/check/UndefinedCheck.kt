@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.auth.check

import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * A pseudo operation that always fails (for unknown checks).
 */
@JsExport
class UndefinedCheck : CompiledCheck() {
    override fun matches(value: Any?): Boolean = false
}
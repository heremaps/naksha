@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.auth.check

import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * Tests if the attribute value is a [String] and ends with at least one of the given arguments.
 */
@JsExport
class EndsWithCheck : Check() {
    companion object {
        @JvmStatic
        @JsStatic
        val NAME = "endsWith"
    }

    override fun matches(value: Any?): Boolean {
        for (arg in this) {
            if (arg is String && value is String && value.endsWith(arg)) return true
        }
        return false
    }
}

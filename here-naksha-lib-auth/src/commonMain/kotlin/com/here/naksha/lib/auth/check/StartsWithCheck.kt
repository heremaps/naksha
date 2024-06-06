@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.auth.check

import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * Tests if the attribute value is a [String] and starts with at least one of the given arguments.
 */
@JsExport
class StartsWithCheck : Check() {
    companion object {
        @JvmStatic
        @JsStatic
        val NAME = "startsWith"
    }

    override fun matches(value: Any?): Boolean {
        for (arg in this) {
            if (arg is String && value is String && value.startsWith(arg)) return true
        }
        return false
    }
}
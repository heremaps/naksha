@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.auth.check

import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * Tests if the attribute value equals at least one of the arguments.
 */
@JsExport
class EqualsCheck : Check() {
    companion object {
        @JvmStatic
        @JsStatic
        val NAME = "equals"
    }

    override fun matches(value: Any?): Boolean {
        for (arg in this) {
            if (arg == value) return true
        }
        return false
    }
}
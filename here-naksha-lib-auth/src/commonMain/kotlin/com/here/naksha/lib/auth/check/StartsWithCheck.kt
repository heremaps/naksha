@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.auth.check

import kotlin.js.JsExport

/**
 * Tests if the attribute value is a [String] and starts with at least one of the given arguments.
 */
@JsExport
class StartsWithCheck : Check() {

    override fun matches(value: Any?): Boolean {
        if (value !is String) {
            return false
        }
        return filterIsInstance<String>()
            .any { arg -> value.startsWith(arg) }
    }
}
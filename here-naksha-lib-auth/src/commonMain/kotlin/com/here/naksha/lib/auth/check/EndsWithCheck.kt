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
    override fun matches(value: Any?): Boolean {
        if(value !is String){
            return false
        }
        return filterIsInstance<String>().any { arg -> value.endsWith(arg) }
    }
}

@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.auth.check

import kotlin.js.JsExport

/**
 * Tests if the attribute value equals at least one of the arguments.
 */
@JsExport
class EqualsCheck : Check() {
    override fun matches(value: Any?): Boolean =
        any { arg -> arg == value }
}
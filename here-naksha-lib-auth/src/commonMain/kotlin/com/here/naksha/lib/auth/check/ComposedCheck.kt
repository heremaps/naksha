package com.here.naksha.lib.auth.check

import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * This check delegates compilation of dependent checks basing on initial arguments.
 * It tests if for all values passed to the [ComposedCheck.matches], at least one of compiled checks passes.
 */
@JsExport
class ComposedCheck() : CompiledCheck() {

    @JsName("withArgs")
    constructor(vararg checks: Any) : this() {
        addAll(checks)
    }

    override fun matches(value: Any?): Boolean {
        val checks = mapNotNull { checkValue ->
            checkValue?.let { CheckCompiler.compile(it) }
        }
        return if (value is List<*>) {
            checks.all { check ->
                value.any { check.matches(it) }
            }
        } else {
            atLeastSingleCheckMatches(value, checks)
        }
    }

    companion object {
        private fun atLeastSingleCheckMatches(
            value: Any?,
            checks: List<CompiledCheck>
        ): Boolean {
            return checks.any { check ->
                check.matches(value)
            }
        }

    }
}
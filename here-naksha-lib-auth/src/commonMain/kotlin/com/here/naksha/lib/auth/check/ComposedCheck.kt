package com.here.naksha.lib.auth.check

import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * This check delegates compilation of dependent checks basing on initial arguments.
 * All checks must be satisfied in order for the composed check to pass.
 *
 * Given these raw args:
 * [
 *     "my-unique-tag",
 *     "some-common-tag-with-wild-card-*"
 * ]
 *
 * The compiled check would look as follows:
 * [
 *     EqualsCheck("my-unique-tag"),
 *     StartsWithCheck("some-common-tag-with-wild-card-")
 * ]
 *
 * Hence, since all the compiled checks must be satisfied by values the following example would pass
 * [
 *     "my-unique-tag",                         // satisfies first check
 *     "some-common-tag-with-wild-card-SUFFIX", // satisfies second check
 *     "some_other_tag"                         // doesn't satisfy anything but other values already did
 * ]
 *
 * This example however, would fail because 'StartWithCheck` is not satisfied by anything
 * [
 *     "my-unique-tag",                         // satisfies first check
 *     "some_other_tag"                         // doesn't satisfy anything
 * ]
 *
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
            allChecksAreSatisfiedByAtLeastOneValue(checks, value)
        } else {
            allChecksAreSatisfiedByValue(checks, value)
        }
    }

    companion object {
        private fun allChecksAreSatisfiedByAtLeastOneValue(
            checks: List<CompiledCheck>,
            values: List<Any?>
        ): Boolean {
            return checks.all { check ->
                values.any { check.matches(it) }
            }
        }

        private fun allChecksAreSatisfiedByValue(
            checks: List<CompiledCheck>,
            value: Any?
        ): Boolean {
            return checks.all { check -> check.matches(value) }
        }
    }
}
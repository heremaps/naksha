package com.here.naksha.lib.auth.check

import com.here.naksha.lib.base.com.here.naksha.lib.auth.UserRights
import naksha.base.AbstractListProxy
import naksha.base.PlatformList
import naksha.base.PlatformListApi.Companion.array_entries

object CheckCompiler {

    private const val WILDCARD = "*"

    /**
     * Compiler's main function that takes in raw (uncompiled) map of properties ([UserRights] and
     * their raw checks, and compiles them to specific instances of [CompiledCheck] for each of property.
     * This is later used in for matrix matching (see [UserRights.matches])
     *
     * IN:
     * {
     *    "foo": "prefix-*",
     *    "bar": "*-suffix",
     *    "xyz": "strict"
     * }
     *
     * OUT:
     * {
     *    "foo": StartsWithCheck("prefix"),
     *    "bar": EndsWithCheck("suffix"),
     *    "xyz": EqualsCheck("strict")
     * }
     */
    fun compile(userRights: UserRights): Map<String, CompiledCheck> {
        return userRights.asSequence().associate { (key, value) ->
            key to compile(requireNotNull(value) { "Values shouldn't be null" })
        }
    }

    fun compile(value: Any): CompiledCheck {
        return when (value) {
            is String -> getStringCheckFor(value)
            is PlatformList -> getListCheckFor(value)
            is AbstractListProxy<*> -> getListCheckFor(value.data())
            else -> UndefinedCheck().apply { add(value) }
        }
    }

    private fun getStringCheckFor(value: String): CompiledCheck {
        return if (value.startsWith(WILDCARD)) {
            EndsWithCheck(value.substringAfter(WILDCARD))
        } else if (value.endsWith(WILDCARD)) {
            StartsWithCheck(value.substringBefore(WILDCARD))
        } else {
            EqualsCheck(value)
        }
    }

    private fun getListCheckFor(value: PlatformList): CompiledCheck {
        val platformIterator = array_entries(value)
        val composedCheck = ComposedCheck()
        generateSequence(platformIterator.next().value) {
            platformIterator.next().value
        }.forEach { composedCheck.add(it) }
        return composedCheck
    }
}

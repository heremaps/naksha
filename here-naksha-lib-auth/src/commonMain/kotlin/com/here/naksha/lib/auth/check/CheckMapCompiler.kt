package com.here.naksha.lib.auth.check

import com.here.naksha.lib.base.com.here.naksha.lib.auth.UserRights
import naksha.base.PlatformList
import naksha.base.PlatformListApi.Companion.array_entries

object CheckMapCompiler {

    private const val WILDCARD = "*"

    /**
     * Compiler's main function that takes in raw (uncompiled) map of properties ([UserRights] and
     * their raw checks, and compiles them to specific instances of [Check] for each of property.
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
    fun compile(userRights: UserRights): Map<String, Check> {
        return userRights.asSequence().associate { (key, value) ->
            key to checkFor(requireNotNull(value) { "Values shouldn't be null" })
        }
    }

    private fun checkFor(value: Any): Check {
        return when (value) {
            is String -> getStringCheckFor(value)
            is PlatformList -> getListCheckFor(value)
            else -> UndefinedCheck().apply { add(value) }
        }
    }

    private fun getStringCheckFor(value: String): Check {
        return if (value.startsWith(WILDCARD)) {
            EndsWithCheck().apply { add(value.substringAfter(WILDCARD)) }
        } else if (value.endsWith(WILDCARD)) {
            StartsWithCheck().apply { add(value.substringBefore(WILDCARD)) }
        } else {
            EqualsCheck().apply { add(value) }
        }
    }

    private fun getListCheckFor(value: PlatformList): Check {
        val platformIterator = array_entries(value)
        val check = EqualsCheck()
        generateSequence(platformIterator.next().value) {
            platformIterator.next().value
        }.forEach { check.add(it) }
        return check
    }
}

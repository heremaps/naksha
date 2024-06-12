package com.here.naksha.lib.auth.check

import com.here.naksha.lib.base.com.here.naksha.lib.auth.RawCheckMap
import naksha.base.PlatformList
import naksha.base.PlatformListApi.Companion.array_entries
import naksha.base.Proxy

object CheckMapCompiler {

    private const val WILDCARD = "*"

    fun compile(rawCheckMap: RawCheckMap): CheckMap {
        val compiledMap = rawCheckMap.mapValues { (_, value) ->
            checkFor(requireNotNull(value) { "Values shouldn't be null" })
        }
        return requireNotNull(Proxy.box(compiledMap, CheckMap::class))
    }

    private fun checkFor(value: Any): Check {
        return when (value) {
            is String -> getStringCheckFor(value)
            is PlatformList -> getListCheckFor(value)
            else -> UnknownOp().apply { add(value) }
        }
    }

    private fun getStringCheckFor(value: String): Check {
        return if (value.startsWith(WILDCARD)) {
            StartsWithCheck()
        } else if (value.endsWith(WILDCARD)) {
            EndsWithCheck()
        } else {
            EqualsCheck()
        }.apply { add(value) }
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

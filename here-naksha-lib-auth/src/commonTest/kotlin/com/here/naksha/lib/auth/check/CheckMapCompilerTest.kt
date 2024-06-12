package com.here.naksha.lib.auth.check

import com.here.naksha.lib.base.com.here.naksha.lib.auth.UserRights
import naksha.base.P_List
import naksha.base.PlatformList
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertIs

class CheckMapCompilerTest {

    @Test
    fun shouldCompileStringChecksForUserRights() {
        // Given:
        val userRights = UserRights()
            .withPropertyCheck("foo", "prefix-*")
            .withPropertyCheck("bar", "*-suffix")
            .withPropertyCheck("xyz", "strict")

        // When:
        val checkMap = CheckMapCompiler.compile(userRights)

        // Then:
        assertIs<StartsWithCheck>(checkMap["foo"])
        assertIs<EndsWithCheck>(checkMap["bar"])
        assertIs<EqualsCheck>(checkMap["xyz"])
    }

    @Test
    fun shouldCompileListCheckForUserRights() {
        // Given:
        val userRights = UserRights()
            .withPropertyCheck("list", testPlatformList)

        // When:
        val checkMap = CheckMapCompiler.compile(userRights)

        // Then:
        assertIs<EqualsCheck>(checkMap["list"])
    }

    @Test
    fun shouldReturnUndefinedCheckForUnknownValue() {
        // Given:
        val userRights = UserRights()
            .withPropertyCheck("unsupported_object", object {})

        // When:
        val checkMap = CheckMapCompiler.compile(userRights)

        // Then:
        assertIs<UndefinedCheck>(checkMap["unsupported_object"])
    }

    private val testPlatformList = object : PlatformList {
        override fun <T : P_List<*>> proxy(klass: KClass<T>, doNotOverride: Boolean): T =
            throw NotImplementedError("This should not be called in test")
    }
}
package com.here.naksha.lib.auth.check

import com.here.naksha.lib.auth.check.CheckMapCompilerTest.CheckAssertion.Companion.assertThat
import com.here.naksha.lib.base.com.here.naksha.lib.auth.UserRights
import naksha.base.P_StringList
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class CheckMapCompilerTest {

    @Test
    fun shouldCompileStringChecksForUserRights() {
        // Given:
        val userRights = UserRights()
            .withPropertyCheck("foo", "prefix-*")
            .withPropertyCheck("bar", "*-suffix")
            .withPropertyCheck("xyz", "strict")

        // When:
        val checkMap = CheckCompiler.compile(userRights)

        // Then:
        assertThat(checkMap["foo"])
            .isOfType(StartsWithCheck::class)
            .hasArgs("prefix-")

        // And
        assertThat(checkMap["bar"])
            .isOfType(EndsWithCheck::class)
            .hasArgs("-suffix")

        // And
        assertThat(checkMap["xyz"])
            .isOfType(EqualsCheck::class)
            .hasArgs("strict")
    }

    @Test
    fun shouldCompileListCheckForUserRights() {
        // Given:
        val userRights = UserRights()
            .withPropertyCheck("list", P_StringList()
                .apply {
                    add("foo")
                    add("bar")
                    add("buzz")
                }
            )

        // When:
        val checkMap = CheckCompiler.compile(userRights)

        // Then:
        assertThat(checkMap["list"])
            .isOfType(ComposedCheck::class)
            .hasArgs("foo", "bar", "buzz")
    }

    @Test
    fun shouldReturnUndefinedCheckForUnknownValue() {
        // Given:
        val userRights = UserRights()
            .withPropertyCheck("unsupported_object", object {})

        // When:
        val checkMap = CheckCompiler.compile(userRights)

        // Then:
        assertThat(checkMap["unsupported_object"])
            .isOfType(UndefinedCheck::class)
    }

    class CheckAssertion private constructor(val subject: CompiledCheck){
        companion object {
            fun assertThat(check: CompiledCheck?): CheckAssertion {
                assertNotNull(check)
                return CheckAssertion(check)
            }
        }

        inline fun <reified T: CompiledCheck> isOfType(type: KClass<T>): CheckAssertion = apply {
            assertIs<T>(subject)
        }

        fun hasArgs(vararg args: Any): CheckAssertion = apply {
            assertContentEquals(args.toList(), subject)
        }
    }
}
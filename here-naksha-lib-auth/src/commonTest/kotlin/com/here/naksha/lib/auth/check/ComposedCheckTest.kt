package com.here.naksha.lib.auth.check

import kotlin.test.Test
import kotlin.test.assertTrue

class ComposedCheckTest {

    @Test
    fun shouldPass() {
        // Given:
        val composedCheck = ComposedCheck("foo", "bar*", "*buzz")

        // And:
        val positiveScenarios = listOf(
            listOf("foo", "bar_", "_buzz"),
            listOf("foo"),
            listOf("bar_", "_buzz"),
            listOf("bar_buzz"),
            "foo",
            "bar_",
            "_buzz"
        )

        // Then:
        positiveScenarios.forEach {
            assertTrue { composedCheck.matches(it) }
        }
    }
}
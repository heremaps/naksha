package com.here.naksha.lib.auth.check

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComposedCheckTest {

    @Test
    fun shouldPass() {
        // Given: check done on top of URM
        val composedCheck = ComposedCheck("foo", "bar*", "*buzz")

        // And: values present in ARM
        val positiveScenarios = listOf(
            listOf("foo", "bar_", "_buzz"),
            listOf("foo","bar_buzz"),
        )

        // Then:
        positiveScenarios.forEach {
            assertTrue("Failed for scenario: $it") { composedCheck.matches(it) }
        }
    }
    @Test
    fun shouldFail() {
        // Given: check done on top of URM
        val composedCheck = ComposedCheck("foo", "bar*", "*buzz")

        // And: values present in ARM
        val negativeScenarios = listOf(
            listOf("foo"),
            listOf("bar_", "_buzz"),
            listOf("bar_buzz"),
            "foo",
            "bar_",
            "_buzz"
        )

        // Then:
        negativeScenarios.forEach {
            assertFalse("Succeeded for negative scenario: $it") { composedCheck.matches(it) }
        }
    }
}
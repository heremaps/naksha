package com.here.naksha.lib.auth

import kotlin.test.Test
import kotlin.test.assertEquals

class AccessMatcherTest {

    @Test
    fun shouldMatchSingleStringScenarios() {
        SingleStringScenario.SCENARIOS.forEach { (userAttribute, accessAttribute, outcome) ->
            MatcherSelector.selectMatcherFor(userAttribute)
                .matches(accessAttribute)
                .let { assertEquals(outcome, it) }
        }
    }

    @Test
    fun shouldMatchCompositeScenarios() {
        CompositeScenario.SCENARIOS.forEach { (userAttributes, accessAttributes, outcome) ->
            MatcherSelector.selectMatcherFor(userAttributes)
                .matches(accessAttributes)
                .let { assertEquals(outcome, it) }
        }
    }

    data class SingleStringScenario(
        val userAttribute: String,
        val accessAttribute: String,
        val outcome: Boolean
    ) {

        companion object {
            val SCENARIOS = listOf(
                SingleStringScenario(
                    userAttribute = "simple_string",
                    accessAttribute = "simple_string",
                    outcome = true
                ),
                SingleStringScenario(
                    userAttribute = "simple_string",
                    accessAttribute = "other_simple_string",
                    outcome = false
                ),
                SingleStringScenario(
                    userAttribute = "with_wildcard_*",
                    accessAttribute = "with_wildcard_123",
                    outcome = true
                ),
                SingleStringScenario(
                    userAttribute = "with_*_wildcard",
                    accessAttribute = "with_123_wildcard",
                    outcome = true
                ),
                SingleStringScenario(
                    userAttribute = "*_with_wildcard",
                    accessAttribute = "123_with_wildcard",
                    outcome = true
                ),
                SingleStringScenario(
                    userAttribute = "with_wildcard_*",
                    accessAttribute = "123_with_wildcard",
                    outcome = false
                ),
                SingleStringScenario(
                    userAttribute = "with_*_wildcard",
                    accessAttribute = "with_wildcard_123",
                    outcome = false
                ),
                SingleStringScenario(
                    userAttribute = "*_with_wildcard",
                    accessAttribute = "with_wildcard_123",
                    outcome = false
                )
            )
        }
    }

    data class CompositeScenario(
        val userAttribute: List<String>,
        val accessAttribute: List<String>,
        val outcome: Boolean
    ) {
        companion object {
            val SCENARIOS = listOf(
                CompositeScenario(
                    // urm (left side)
                    userAttribute = listOf(
                        "simple_string_1",
                        "with_*_wildcard"
                    ),
                    // arm (right side)
                    accessAttribute = listOf(
                        "simple_string_1",
                        "with_123_wildcard",
                        "you can have additional entries in ARM",
                    ),
                    outcome = true
                ),
                CompositeScenario(
                    // urm (left side)
                    userAttribute = listOf(
                        "simple_string_1",
                        "with_*_wildcard",
                        "you can not have additional entries in URM",
                    ),
                    // arm (right side)
                    accessAttribute = listOf(
                        "simple_string_1",
                        "with_123_wildcard",
                    ),
                    outcome = false
                ),
            )
        }
    }
}
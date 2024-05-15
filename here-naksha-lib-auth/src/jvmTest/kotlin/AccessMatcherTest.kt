import com.here.naksha.lib.auth.MatcherSelector
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

class AccessMatcherTest {

    @ParameterizedTest
    @MethodSource("singleStringScenarios")
    fun `should perform matching against single string`(
        userAttribute: String,
        accessAttribute: String,
        outcome: Boolean
    ) {
        MatcherSelector
            .selectMatcherFor(userAttribute)
            .matches(accessAttribute)
            .assert(outcome)
    }

    @ParameterizedTest
    @MethodSource("compositeScenarios")
    fun `should perform matching against array of strings`(
        userAttribute: List<String>,
        accessAttribute: List<String>,
        outcome: Boolean
    ) {
        MatcherSelector
            .selectMatcherFor(userAttribute)
            .matches(accessAttribute)
            .assert(outcome)
    }

    companion object {
        @JvmStatic
        fun singleStringScenarios() = listOf(
            arguments("simple_string", "simple_string", true),
            arguments("simple_string", "other_simple_string", false),
            arguments("with_wildcard_*", "with_wildcard_123", true),
            arguments("with_*_wildcard", "with_123_wildcard", true),
            arguments("*_with_wildcard", "123_with_wildcard", true),
            arguments("with_wildcard_*", "123_with_wildcard", false),
            arguments("with_*_wildcard", "with_wildcard_123", false),
            arguments("*_with_wildcard", "with_wildcard_123", false)
        )

        @JvmStatic
        fun compositeScenarios() = listOf(
            arguments(
                // urm (left side)
                listOf(
                    "simple_string_1",
                    "with_*_wildcard"
                ),
                // arm (right side)
                listOf(
                    "simple_string_1",
                    "with_123_wildcard",
                    "you can have additional entries in ARM",
                ),
                true
            ),
            arguments(
                // urm (left side)
                listOf(
                    "simple_string_1",
                    "with_*_wildcard",
                    "you can not have additional entries in URM",
                ),
                // arm (right side)
                listOf(
                    "simple_string_1",
                    "with_123_wildcard",
                ),
                false
            ),
        )
    }
}
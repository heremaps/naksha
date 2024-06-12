
abstract class AccessMatcher(open val userAttribute: Any) {

    abstract fun matches(accessAttribute: Any): Boolean
}

object MatcherSelector {

    private const val WILDCARD: String = "*";
    private const val EXPANDED_WILDCARD = ".*"

    fun selectMatcherFor(userAttribute: Any?): AccessMatcher {
        return when (userAttribute) {
            is String -> singleStringMatcherFor(userAttribute)
            is List<*> -> Composite(userAttribute as List<String>)
            else -> throw MatcherNotFoundException(userAttribute)
        }
    }

    fun singleStringMatcherFor(userAttribute: String): AccessMatcher {
        return if (userAttribute.contains(WILDCARD)) {
            Regexp(userAttribute.asRegexWithExpandedWildcard())
        } else {
            Equality(userAttribute)
        }
    }

    private fun String.asRegexWithExpandedWildcard(): Regex =
        Regex(replace(WILDCARD, EXPANDED_WILDCARD))
}

class MatcherNotFoundException(accessAttribute: Any?) :
    IllegalArgumentException("Could not find matcher for accessAttribute: $accessAttribute")

private class Equality(override val userAttribute: String) : AccessMatcher(userAttribute) {
    override fun matches(accessAttribute: Any): Boolean =
        userAttribute == accessAttribute
}

private class Regexp(override val userAttribute: Regex) : AccessMatcher(userAttribute) {
    override fun matches(accessAttribute: Any): Boolean =
        (accessAttribute as String).matches(userAttribute)
}

private class Composite(override val userAttribute: List<String>) : AccessMatcher(userAttribute) {

    override fun matches(accessAttribute: Any): Boolean {
        accessAttribute as List<String>
        if (accessAttribute.size < userAttribute.size) {
            return false
        }
        return userAttribute.zip(accessAttribute)
            .all { (user, access) ->
                MatcherSelector.singleStringMatcherFor(user)
                    .matches(access)
            }
    }
}

package com.here.naksha.lib.base.com.here.naksha.lib.auth

import com.here.naksha.lib.auth.AccessAttributeMap
import com.here.naksha.lib.auth.AccessMatcher
import com.here.naksha.lib.auth.MatcherSelector

object MatcherCompiler {

    fun compile(urmAttributes: UserAttributeMap): CompiledUserAttributesMap {
        val matchersByUserAttribute =
            urmAttributes.mapValues { (_, value) -> MatcherSelector.selectMatcherFor(value) }
        return CompiledUserAttributesMap(matchersByUserAttribute)
    }
}

class CompiledUserAttributesMap(raw: Map<String, AccessMatcher>) :
    Map<String, AccessMatcher> by raw {

    fun matches(accessAttributes: AccessAttributeMap): Boolean {
        return all { (userKey, matcher) ->
            accessAttributes[userKey]
                ?.let { matcher.matches(it) }
                ?: false
        }
    }
}



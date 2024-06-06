package com.here.naksha.lib.base.com.here.naksha.lib.auth

import com.here.naksha.lib.auth.attribute.ResourceAttributes
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

    fun matches(resourceAttributes: ResourceAttributes): Boolean {
        return all { (userKey, matcher) ->
            resourceAttributes[userKey]
                ?.let { matcher.matches(it) }
                ?: false
        }
    }
}



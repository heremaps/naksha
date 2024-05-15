package com.here.naksha.lib.base.com.here.naksha.lib.auth

import com.here.naksha.lib.auth.AccessAttributes
import com.here.naksha.lib.auth.AccessMatcher
import com.here.naksha.lib.auth.MatcherSelector
import com.here.naksha.lib.base.get
import com.here.naksha.lib.base.iterator

object MatcherCompiler {

    fun compile(urmAttributes: UserAttributes): CompiledUserAttributesMap {
        return urmAttributes.data().iterator()
            .asSequence()
            .associate { (key, value) -> key to MatcherSelector.selectMatcherFor(value) }
            .let { CompiledUserAttributesMap(it) }
    }
}

class CompiledUserAttributesMap(raw: Map<String, AccessMatcher>) :
    Map<String, AccessMatcher> by raw {

    fun matches(accessAttributes: AccessAttributes): Boolean {
        val rawAccessAttributes = accessAttributes.data()
        return all { (userKey, matcher) ->
            rawAccessAttributes[userKey]
                ?.let { matcher.matches(it) }
                ?: false
        }
    }
}



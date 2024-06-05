package com.here.naksha.lib.auth.attribute

import com.here.naksha.lib.auth.AccessAttributeMap

abstract class CommonAttributes<SELF : CommonAttributes<SELF>> protected constructor(vararg args: Any) :
    AccessAttributeMap(*args) {
    fun id(id: String): SELF = apply { set(ID_KEY, id) } as SELF
    fun tags(tags: Array<String>): SELF = apply { set(TAGS_KEY, tags) } as SELF
    fun appId(appId: String): SELF = apply { set(APP_ID_KEY, appId) } as SELF
    fun author(author: String): SELF = apply { set(AUTHOR_KEY, author) } as SELF

    fun customAttribute(key: String, value: Any) = apply { set(key, value) } as SELF

    companion object {
        const val ID_KEY = "id"
        const val TAGS_KEY = "tags"
        const val APP_ID_KEY = "appId"
        const val AUTHOR_KEY = "author"
    }
}

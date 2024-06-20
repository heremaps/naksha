@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.auth.attribute

import kotlin.js.JsExport

/**
 * The base attribute map that all Naksha resources share.
 */
@Suppress("UNCHECKED_CAST")
@JsExport
abstract class NakshaAttributes<SELF : NakshaAttributes<SELF>> : ResourceAttributes() {
    fun id(id: String): SELF = apply { set(ID_KEY, id) } as SELF
    fun tags(vararg tags: String): SELF = apply { set(TAGS_KEY, tags) } as SELF
    fun appId(appId: String): SELF = apply { set(APP_ID_KEY, appId) } as SELF
    fun author(author: String): SELF = apply { set(AUTHOR_KEY, author) } as SELF
    fun customAttribute(key: String, value: Any): SELF = apply { set(key, value) } as SELF

    companion object {
        const val ID_KEY = "id"
        const val TAGS_KEY = "tags"
        const val APP_ID_KEY = "appId"
        const val AUTHOR_KEY = "author"
    }
}
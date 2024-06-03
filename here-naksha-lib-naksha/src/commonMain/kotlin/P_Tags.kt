@file:Suppress("OPT_IN_USAGE")
package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@JsExport
class P_Tags : P_List<String>(String::class) {

    fun getTag(key: String): Tag? {
        throw NotImplementedError("implement me!")
    }

    fun addTag(tag: Tag) {
        throw NotImplementedError("implement me!")
    }

    fun removeTag(tag: Tag): Tag? {
        throw NotImplementedError("implement me!")
    }

    fun removeTagByKey(key: String): Tag? {
        throw NotImplementedError("implement me!")
    }
}

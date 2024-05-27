@file:Suppress("OPT_IN_USAGE")
package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@JsExport
class P_Tags() : BaseList<String>() {

    companion object {
        @JvmStatic
        val klass = object : BaseArrayKlass<String, P_Tags>() {
            override fun isInstance(o: Any?): Boolean = o is P_Tags

            override fun newInstance(vararg args: Any?): P_Tags = P_Tags()
        }
    }

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

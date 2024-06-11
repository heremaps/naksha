@file:Suppress("OPT_IN_USAGE")
package naksha.model

import naksha.base.P_List
import kotlin.js.JsExport

@JsExport
class TagsProxy : P_List<String>(String::class) {

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

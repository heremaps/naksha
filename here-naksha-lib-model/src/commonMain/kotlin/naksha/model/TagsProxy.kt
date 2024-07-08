package naksha.model

import naksha.base.ListProxy
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 *
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class TagsProxy() : ListProxy<String>(String::class) {

    @JsName("of")
    constructor(vararg tags: String): this() {
        addAll(tags)
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

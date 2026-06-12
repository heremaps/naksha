@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.ListProxy
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmStatic

/**
 * An ordered list of [Index]es on a [NakshaCollection].
 * @since 3.0
 */
@JsExport
open class IndexList() : ListProxy<Index>(Index::class) {

    /**
     * Construct a list from a vararg of indexes.
     * @since 3.0
     */
    @JsName("of")
    constructor(vararg indexes: Index) : this() {
        for (index in indexes) add(index)
    }

    /**
     * Construct a list from a vararg of indexes.
     * @since 3.0
     */
    @JsName("fromList")
    constructor(indexes: List<Index>) : this() {
        addAll(indexes)
    }

    companion object IndexList_C {
        @JvmStatic
        fun of(vararg indexes: Index): IndexList =
            IndexList().apply { addAll(indexes.toList()) }
    }
}

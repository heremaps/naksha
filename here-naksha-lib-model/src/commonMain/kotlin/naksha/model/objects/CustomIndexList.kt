@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.ListProxy
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmStatic

/**
 * An ordered list of user-defined indexes on a [NakshaCollection].
 * @since 3.0
 */
@JsExport
open class CustomIndexList() : ListProxy<CustomIndex>(CustomIndex::class) {

    /**
     * Construct a list from a vararg of indexes.
     * @since 3.0
     */
    @JsName("fromIndexes")
    constructor(vararg indexes: CustomIndex) : this() {
        addAll(indexes.toList())
    }

    companion object CustomIndexList_C {
        @JvmStatic
        fun of(vararg indexes: CustomIndex): CustomIndexList =
            CustomIndexList().apply { addAll(indexes.toList()) }
    }
}

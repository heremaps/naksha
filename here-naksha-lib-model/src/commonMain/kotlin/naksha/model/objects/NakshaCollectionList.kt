@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.PTypedArray
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * A list of Naksha collections.
 */
@JsExport
open class NakshaCollectionList : PTypedArray<NakshaCollection>(NakshaCollection::class){

    companion object NakshaCollectionList_C {

        @JvmStatic
        fun fromList(collections: List<NakshaCollection>): NakshaCollectionList =
            NakshaCollectionList().apply { addAll(collections) }

        @JvmStatic
        fun of(vararg collections: NakshaCollection): NakshaCollectionList =
            NakshaCollectionList().apply { addAll(collections) }
    }
}
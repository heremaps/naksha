@file:OptIn(ExperimentalJsExport::class)

package naksha.model.objects

import naksha.base.PTypedArray
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * A list of Naksha catalogs.
 */
@JsExport
open class NakshaCatalogList : PTypedArray<NakshaCatalog>(NakshaCatalog::class){

    companion object NakshaCatalogList_C {

        @JvmStatic
        fun fromList(catalogs: List<NakshaCatalog>): NakshaCatalogList =
            NakshaCatalogList().apply { addAll(catalogs) }

        @JvmStatic
        fun of(vararg catalogs: NakshaCatalog): NakshaCatalogList =
            NakshaCatalogList().apply { addAll(catalogs) }
    }
}

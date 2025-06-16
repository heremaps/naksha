@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.ListProxy
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A mutable list of [filters][ResultFilter].
 * @since
 */
@JsExport
open class ResultFilterList : ListProxy<ResultFilter>(ResultFilter_TYPE) {
    companion object ResultFilterList_C {
        /**
         * The [PlatformType] of [ResultFilterList].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(ResultFilterList::class).withPackageName(PACKAGE_NAME)
    }
}

@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.ListProxy
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A list of [Tuple].
 * @since 3.0
 */
@JsExport
class TupleList : ListProxy<Tuple>(Tuple.TYPE) {
    companion object TupleList_C {
        /**
         * The [PlatformType] of [TupleList].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(TupleList::class).withPackageName(PACKAGE_NAME)
    }
}
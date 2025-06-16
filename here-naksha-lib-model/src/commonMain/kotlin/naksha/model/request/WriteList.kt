@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.ListProxy
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A list of [writes][Write] to perform.
 * @since 3.0
 */
@JsExport
class WriteList : ListProxy<Write>(Write.TYPE) {
    companion object WriteList_C {
        /**
         * The [PlatformType] of [WriteList].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(WriteList::class).withPackageName(PACKAGE_NAME)
    }
}
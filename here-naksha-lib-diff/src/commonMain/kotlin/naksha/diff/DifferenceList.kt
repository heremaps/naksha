@file:Suppress("OPT_IN_USAGE")

package naksha.diff

import naksha.base.ListProxy
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A list of [differences][Difference] what changed at this index, if `null`, then nothing changed at this index.
 * @since 3.0
 */
@JsExport
class DifferenceList: ListProxy<Difference>(DIFFERENCE) {
    companion object DifferenceList_C {
        /**
         * The [PlatformType] of [DifferenceList].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(DifferenceList::class).withPackageName(PACKAGE_NAME)
    }
}
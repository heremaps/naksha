@file:Suppress("OPT_IN_USAGE")

package naksha.diff

import naksha.base.Any_TYPE
import naksha.base.MapProxy
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A list of [differences][Difference].
 * @since 3.0
 */
@JsExport
class DifferenceMap: MapProxy<Any, Difference>(Any_TYPE, Difference_TYPE) {
    companion object DifferenceMap_C {
        /**
         * The [PlatformType] of [DifferenceMap].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(DifferenceMap::class).withPackageName(PACKAGE_NAME)
    }
}
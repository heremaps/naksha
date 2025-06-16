package naksha.geo

import naksha.base.ListProxy
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A list of [SpGeometry]'s.
 *
 * @since 3.0
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class SpGeometryList : ListProxy<SpGeometry>(SpGeometry.TYPE) {
    companion object SpGeometryList_C {
        /**
         * The [PlatformType] of [SpGeometryList].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(SpGeometryList::class).withPackageName(PACKAGE_NAME)

        init {
            initialize()
        }
    }
}
@file:Suppress("OPT_IN_USAGE")

package naksha.auth.naksha

import naksha.auth.ServiceOpParamsList
import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A list of [MapParams].
 * @since 3.0
 * @see MapParams
 * @see NakshaOps.createMaps
 * @see NakshaOps.readMaps
 * @see NakshaOps.updateMaps
 * @see NakshaOps.deleteMaps
 */
@JsExport
class MapParamsList : ServiceOpParamsList<MapParams>(MapParams.TYPE) {
    companion object MapParamsListCompanion {
        /**
         * The [PlatformType] of [MapParamsList].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<MapParamsList> = forKClass(MapParamsList::class).withPackageName(PACKAGE_NAME)
    }
}
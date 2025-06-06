@file:Suppress("OPT_IN_USAGE")

package naksha.auth.naksha

import naksha.auth.ServiceOpParamsList
import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A list of [SpaceParams].
 * @since 3.0
 * @see NakshaOps.useSpaces
 * @see NakshaOps.manageSpaces
 */
@JsExport
class SpaceParamsList : ServiceOpParamsList<SpaceParams>(SpaceParams.TYPE) {
    companion object SpaceResourceListCompanion {
        /**
         * The [PlatformType] of [SpaceParamsList].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<SpaceParamsList> = forKClass(SpaceParamsList::class).withPackageName(PACKAGE_NAME)
    }
}
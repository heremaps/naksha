@file:Suppress("OPT_IN_USAGE")

package naksha.auth.naksha

import naksha.auth.ServiceOpParamsList
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A list of [ManageEventHandlersParams].
 * @since 3.0
 * @see ManageEventHandlersParams
 * @see NakshaOps.manageEventHandlers
 */
@JsExport
class ManageEventHandlersParamsList : ServiceOpParamsList<ManageEventHandlersParams>(ManageEventHandlersParams.TYPE) {
    companion object ManageEventHandlersParamsList_C {
        /**
         * The [PlatformType] of [ManageEventHandlersParamsList].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<ManageEventHandlersParamsList> = forKClass(ManageEventHandlersParamsList::class).withPackageName(PACKAGE_NAME)
    }
}
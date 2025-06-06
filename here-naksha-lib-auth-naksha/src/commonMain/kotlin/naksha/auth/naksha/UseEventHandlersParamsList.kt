@file:Suppress("OPT_IN_USAGE")

package naksha.auth.naksha

import naksha.auth.ServiceOpParamsList
import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A list of [UseEventHandlersParams].
 * @since 3.0
 * @see UseEventHandlersParams
 * @see NakshaOps.useEventHandlers
 */
@JsExport
class UseEventHandlersParamsList : ServiceOpParamsList<UseEventHandlersParams>(UseEventHandlersParams.TYPE) {
    companion object UseEventHandlersParamsListCompanion {
        /**
         * The [PlatformType] of [UseEventHandlersParamsList].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<UseEventHandlersParamsList> = forKClass(UseEventHandlersParamsList::class).withPackageName(PACKAGE_NAME)
    }
}
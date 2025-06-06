@file:Suppress("OPT_IN_USAGE")

package naksha.auth.naksha

import naksha.auth.ServiceOpParamsList
import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A list of [FeatureParams].
 * @since 3.0
 * @see FeatureParams
 * @see NakshaOps.createFeatures
 * @see NakshaOps.readFeatures
 * @see NakshaOps.updateFeatures
 * @see NakshaOps.deleteFeatures
 */
@JsExport
class FeatureParamsList : ServiceOpParamsList<FeatureParams>(FeatureParams.TYPE) {
    companion object FeatureParamsListCompanion {
        /**
         * The [PlatformType] of [FeatureParamsList].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<FeatureParamsList> = forKClass(FeatureParamsList::class).withPackageName(PACKAGE_NAME)
    }
}
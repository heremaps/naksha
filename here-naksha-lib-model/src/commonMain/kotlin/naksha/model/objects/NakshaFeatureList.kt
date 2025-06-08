@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.ListProxy
import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * A list of Naksha features.
 */
@JsExport
open class NakshaFeatureList : ListProxy<NakshaFeature>(NakshaFeature.TYPE){

    companion object NakshaFeatureListCompanion {
        /**
         * The [PlatformType] of [NakshaFeatureList].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(NakshaFeatureList::class).withPackageName(PACKAGE_NAME)

        @JvmStatic
        @JsStatic
        fun fromList(features: List<NakshaFeature>): NakshaFeatureList =
            NakshaFeatureList().apply { addAll(features) }

        @JvmStatic
        @JsStatic
        fun of(vararg features: NakshaFeature): NakshaFeatureList =
            NakshaFeatureList().apply { addAll(features) }
    }
}

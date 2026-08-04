@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.PTypedArray
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * A list of Naksha features.
 */
@JsExport
open class NakshaFeatureList : PTypedArray<NakshaFeature>(NakshaFeature::class){

    companion object NakshaFeatureList_C {

        @JvmStatic
        fun fromList(features: List<NakshaFeature>): NakshaFeatureList =
            NakshaFeatureList().apply { addAll(features) }

        @JvmStatic
        fun of(vararg features: NakshaFeature): NakshaFeatureList =
            NakshaFeatureList().apply { addAll(features) }
    }
}

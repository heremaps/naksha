@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.ListProxy
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * A list of Naksha objects.
 * @since 3.0
 * @see NakshaObject
 */
@JsExport
open class NakshaObjectList : ListProxy<NakshaObject>(NakshaObject.TYPE){

    companion object NakshaObjectList_C {
        /**
         * The [PlatformType] of [NakshaObjectList].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(NakshaObjectList::class).withPackageName(PACKAGE_NAME)

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

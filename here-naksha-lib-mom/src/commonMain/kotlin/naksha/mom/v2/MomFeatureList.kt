@file:Suppress("OPT_IN_USAGE")

package naksha.mom.v2

import naksha.base.ListProxy
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import naksha.mom.v2.MomFeature.MomFeature_C
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A list of [MomFeature]'s.
 * @since 3.0
 * @see MomFeatureList
 */
class MomFeatureList : ListProxy<MomFeature>(MomFeature.TYPE) {
    companion object MomFeatureList_C {
        /**
         * The [PlatformType] of [MomFeature_C].
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val TYPE = forKClass(MomFeatureList::class).withPackageName(PACKAGE_NAME)
    }
}
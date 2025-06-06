@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import naksha.base.ListProxy
import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A list of [GeoFeature]'s.
 * @since 3.0
 */
@JsExport
open class GeoFeatureList() : ListProxy<GeoFeature>(GeoFeature.TYPE) {

    @JsName("GeoFeatureListOf")
    constructor(vararg features: GeoFeature) : this() {
        addAll(features)
    }

    companion object GeoFeatureListCompanion {
        /**
         * The [PlatformType] of [GeoFeatureList].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<GeoFeatureList> = forKClass(GeoFeatureList::class).withPackageName(PACKAGE_NAME)
    }
}
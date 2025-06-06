@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import naksha.base.AnyObject
import naksha.base.NotNullProperty
import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A [GeoJSON feature collection](https://datatracker.ietf.org/doc/html/rfc7946#section-3.3).
 * @since 3.0
 */
@JsExport
open class GeoCollection() : AnyObject() {

    @JsName("GeoCollectionOf")
    constructor(vararg features: GeoFeature) : this() {
        this.features.addAll(features)
    }

    companion object GeoCollectionCompanion {
        /**
         * The [PlatformType] of [GeoCollection].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<GeoCollection> = forKClass(GeoCollection::class)
            .withPackageName(PACKAGE_NAME)
            .withJsonType("FeatureCollection")

        private val FEATURES_MEMBER = NotNullProperty<GeoCollection, GeoFeatureList>(GeoFeatureList.TYPE) { _, _ -> GeoFeatureList() }
    }

    /**
     * The features of the collection.
     * @since 3.0
     */
    var features: GeoFeatureList by FEATURES_MEMBER
}

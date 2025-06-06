@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import naksha.base.AnyObject
import naksha.base.NotNullProperty
import kotlin.js.JsExport

/**
 * A [GeoJSON Feature collection](https://datatracker.ietf.org/doc/html/rfc7946#section-3.3).
 * @since 3.0
 */
@JsExport
open class GeoFeatureCollection : AnyObject() {
    companion object GeoFeatureCollectionCompanion {
        private val FEATURES = NotNullProperty<GeoFeatureCollection, GeoFeatureList>(GeoFeatureList::class) { _, _ -> GeoFeatureList() }
    }

    /**
     * The features of the collection.
     * @since 3.0
     */
    var features by FEATURES
}

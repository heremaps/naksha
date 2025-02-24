@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import naksha.base.AnyObject
import naksha.base.NotNullProperty
import kotlin.js.JsExport

// Implement me!
// TODO: https://datatracker.ietf.org/doc/html/rfc7946#section-3.3

@JsExport
class SpFeatureCollection : AnyObject() {
    companion object SpFeatureCollection_C {
        private val FEATURES = NotNullProperty<SpFeatureCollection, SpFeatureList>(SpFeatureList::class) { _, _ -> SpFeatureList() }
    }

    /**
     * The features of the collection.
     * @since 3.0
     */
    var features by FEATURES
}

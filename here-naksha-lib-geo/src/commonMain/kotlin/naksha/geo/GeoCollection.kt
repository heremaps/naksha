@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import naksha.base.*
import naksha.base.Platform.Platform_C.forKClass
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A [GeoJSON feature collection](https://datatracker.ietf.org/doc/html/rfc7946#section-3.3).
 * @since 3.0
 */
@JsExport
open class GeoCollection() : AnyTypedObject() {

    @JsName("GeoCollectionOf")
    constructor(vararg features: GeoFeature) : this() {
        this.getFeatures(GeoFeatureList.TYPE).addAll(features)
    }

    companion object GeoCollection_C {
        /**
         * The [PlatformType] of [GeoCollection].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<GeoCollection> = forKClass(GeoCollection::class)
            .withPackageName(PACKAGE_NAME)
            .withJsonType(FEATURE_COLLECTION)
            .withIsFeatureCollection(true)

        /**
         * The constant for the string `"features"`.
         * @since 3.0
         */
        const val FEATURES = "features"

        init {
            initialize()
        }
    }

    /**
     * Returns the features of the collection.
     * @param type The type of the feature list to return.
     * @return the list.
     * @since 3.0
     * @see getFeatures
     * @see setFeatures
     * @see removeFeatures
     * @see clearFeatures
     */
    open fun <F : GeoFeature, LIST : ListProxy<F>> getFeatures(type: PlatformType<LIST>): LIST {
        val raw = getRaw(FEATURES)
        if (raw is PlatformList) return type.proxy(raw)
        val list = type.newInstance()
        set(FEATURES, list)
        return list
    }

    /**
     * Sets the features of the collection.
     * @param list The list of the features.
     * @since 3.0
     * @see getFeatures
     * @see setFeatures
     * @see removeFeatures
     * @see clearFeatures
     */
    open fun <F : GeoFeature, LIST : List<F?>> setFeatures(list: LIST) {
        set(FEATURES, ListProxy.to(GeoFeatureList.TYPE, list))
    }

    /**
     * Sets the features of the collection.
     * @param type The type of the feature list to return.
     * @return the previously set features cast to the given type or `null`, if no features where set.
     * @since 3.0
     * @see getFeatures
     * @see setFeatures
     * @see removeFeatures
     * @see clearFeatures
     */
    open fun <F : GeoFeature, LIST : ListProxy<F>> removeFeatures(type: PlatformType<LIST>): LIST? {
        val old = getFeatures(type)
        removeRaw(FEATURES)
        return old
    }

    /**
     * Remove all `features` from the collection.
     * @return this.
     * @since 3.0
     * @see getFeatures
     * @see setFeatures
     * @see removeFeatures
     * @see clearFeatures
     */
    open fun clearFeatures() {
        removeRaw(FEATURES)
    }
}

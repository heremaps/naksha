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

    override fun withType(type: String?): GeoCollection = super.withType(type) as GeoCollection

    /**
     * Returns the features of the collection.
     * @param type The type of the feature list to return.
     * @return the list.
     * @since 3.0
     * @see getFeatures
     * @see setFeatures
     * @see withFeatures
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
     * @see withFeatures
     * @see clearFeatures
     */
    open fun <F : GeoFeature, LIST : ListProxy<F>> setFeatures(list: LIST) {
        set(FEATURES, list)
    }

    /**
     * Sets the features of the collection.
     * @param list The list of the features.
     * @return this.
     * @since 3.0
     * @see getFeatures
     * @see setFeatures
     * @see withFeatures
     * @see clearFeatures
     */
    open fun <F : GeoFeature, LIST : ListProxy<F>> withFeatures(list: LIST): GeoCollection {
        setFeatures(list)
        return this
    }

    /**
     * Remove all `features` from the collection.
     * @return this.
     * @since 3.0
     * @see getFeatures
     * @see setFeatures
     * @see withFeatures
     * @see clearFeatures
     */
    open fun clearFeatures(): GeoCollection {
        removeRaw(FEATURES)
        return this
    }
}

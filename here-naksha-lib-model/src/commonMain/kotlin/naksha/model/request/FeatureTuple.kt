@file:Suppress("OPT_IN_USAGE", "unused")

package naksha.model.request

import naksha.base.Platform
import naksha.model.*
import naksha.model.objects.NakshaFeature
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * A feature tuple is a wrapper for a [Tuple], and its in-memory representation, the [NakshaFeature]. It allows to lazy load the data of the [Tuple], to cache the [NakshaFeature], and is part of the cache subsystem. A feature tuple is not thread-safe, it is for thread local processing.
 *
 * Assume for example, there are 500,000 tuples part of a bounding box query result. It is most often not useful to load all of them into memory, but we need at least the identifiers of them, the [tuple-numbers][TupleNumber], to know that they are part of the result-set. Then we can process step-wise through the result-set, and stop, when enough have been processed, for example after 1,000. Actually, loading [Tuple] by [Tuple] from the cache does not make sense either, we should load in chunks, because of this, the [caches][ITupleCache] do only allow loading of multiple [FeatureTuple].
 * @since 3.0.0
 */
@JsExport
open class FeatureTuple(
    /**
     * The tuple-number of the result entry.
     * @since 3.0.0
     */
    @JvmField val tupleNumber: TupleNumber,

    /**
     * The [Tuple], _null_ when not yet fetched from a [storage][IStorage] or [cache][ITupleCache].
     * @since 3.0.0
     */
    @JvmField var tuple: Tuple? = null
) {
    /**
     * If the [tuple] is loaded, the source from which it was loaded, being either [IStorage] or [ITupleCache].
     * @since 3.0.0
     */
    @JvmField var source: Any? = null

    /**
     * Returns the feature-id, if available.
     * @return the feature-id, if available.
     * @since 3.0.0
     */
    val id: String?
        get() = tuple?.meta?.id

    private var doNotUpdate: Boolean = false
    private var cachedTuple: Tuple? = null
    private var cachedFeature: NakshaFeature? = null
    private var cachedJson: String? = null

    /**
     * Convert the tuple into a feature, and cache the feature; the value is _null_, if the [tuple] is _null_.
     *
     * - Setting this value to _null_, will just reset the cache, and cause it to be re-created the next time it is read.
     * - Setting the value to an explicit [NakshaFeature] will disable the automatic cache updates, when the [tuple] is modified.
     * - **Beware**: If the returned feature is modified, this will as well modify the cached version.
     * @since 3.0.0
     */
    open var feature: NakshaFeature?
        get() {
            var feature = cachedFeature
            val tuple = this.tuple
            if (tuple != null && tuple !== cachedTuple && !doNotUpdate) {
                feature = tuple.toNakshaFeature()
                cachedFeature = feature
                cachedJson = null
            }
            return feature
        }
        set(value) {
            doNotUpdate = value != null
            cachedFeature = value
            cachedTuple = this.tuple
            cachedJson = null
        }

    /**
     * Convert the [feature] into a JSON, and cache it; the value is _null_, if the [feature] is _null_.
     *
     * The method recognized manually set features, and re-calculates the JSON for them!
     * @since 3.0.0
     */
    val json: String?
        get() {
            var json = cachedJson
            val tuple = this.tuple
            if (tuple != null && tuple !== cachedTuple) {
                json = Platform.toJSON(feature)
                cachedJson = json
            }
            return json
        }

    /**
     * Convert the tuple into a new feature, bypassing the cache and not updating the cache.
     *
     * @return a new copy of the tuple converted into a feature.
     * @since 3.0.0
     */
    open fun newFeature(): NakshaFeature? = tuple?.toNakshaFeature()

    companion object ResultTuple_C {
        /**
         * Helper mainly for Java to invoke the constructor with defaults for a [TupleNumber]; this method will load the [Tuple] from the [NakshaCache], if it is contained in it.
         * @param tupleNumber the [TupleNumber] for which to create a [FeatureTuple].
         * @return the [FeatureTuple].
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun fromTupleNumber(tupleNumber: TupleNumber): FeatureTuple = FeatureTuple(tupleNumber)
    }
}
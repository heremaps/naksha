@file:Suppress("OPT_IN_USAGE", "unused")

package naksha.model.request

import naksha.base.Platform
import naksha.model.*
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StandardMembers
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmField

/**
 * A feature tuple is a wrapper for a [Tuple], and its in-memory representation, the [NakshaFeature]. It allows to lazy load the data of the [Tuple], to cache the [NakshaFeature], and is part of the cache subsystem. A feature tuple is not thread-safe, it is for thread local processing.
 *
 * Assume for example, there are 500,000 tuples part of a bounding box query result. It is most often not useful to load all of them into memory, but we need at least the identifiers of them, the [tuple-numbers][TupleNumber], to know that they are part of the result-set. Then we can process step-wise through the result-set, and stop, when enough have been processed, for example after 1,000. Actually, loading [Tuple] by [Tuple] from the cache does not make sense either, we should load in chunks, because of this, the [caches][ITupleCache] do only allow loading of multiple [FeatureTuple].
 * @since 3.0
 */
@JsExport
open class FeatureTuple(
    /**
     * The tuple-number of the result entry.
     * @since 3.0
     */
    @JvmField val tupleNumber: TupleNumber,

    /**
     * The [Tuple], `null` when not yet fetched from a [storage][IStorage] or [cache][ITupleCache].
     * @since 3.0
     * @see [TupleCache.get]
     */
    @JvmField var tuple: Tuple? = Naksha.cache[tupleNumber]
) {

    /**
     * Create a feature-tuple from a [NakshaFeature].
     * @param feature the [NakshaFeature] from which to create this [FeatureTuple].
     * @since 3.0
     */
    @JsName("fromNakshaFeature")
    @Suppress("LeakingThis")
    constructor(feature: NakshaFeature) : this(feature.tupleNumber) {
        this.feature = feature
    }

    /**
     * If the [tuple] is loaded, the source from which it was loaded, being either [IStorage] or [ITupleCache].
     * @since 3.0
     */
    @JvmField var source: Any? = null

    /**
     * Returns the feature-id, if available.
     * @return the feature-id, if available.
     * @since 3.0
     */
    val id: String?
        get() {
            val member = tuple?.getStringMember(StandardMembers.Id)
            if (member != null) return member
            return feature?.id
        }

    private var doNotAutoUpdate: Boolean = false
    private var cachedTuple: Tuple? = null
    private var cachedFeature: NakshaFeature? = null
    private var cachedJson: String? = null

    /**
     * Convert the tuple into a [NakshaFeature] and cache it; the value is `null`, if the [tuple] is `null`.
     *
     * - Setting this value to `null`, will just reset the cache, and cause it to be re-created the next time it is read.
     * - Setting the value to an explicit [NakshaFeature] will disable the automatic cache updates, when the [tuple] is modified.
     * - **Beware**: If the returned feature is modified, this will as well modify the cached version.
     * @since 3.0
     * @see [Tuple.toNakshaFeature]
     */
    open var feature: NakshaFeature?
        get() {
            var feature = cachedFeature
            val tuple = this.tuple
            if (tuple != null && tuple !== cachedTuple && !doNotAutoUpdate) {
                feature = tuple.toNakshaFeature()
                cachedFeature = feature
                cachedJson = null
            }
            return feature
        }
        set(value) {
            doNotAutoUpdate = value != null
            cachedFeature = value
            cachedTuple = this.tuple
            cachedJson = null
        }

    /**
     * Convert the [feature] into a JSON, and cache it; the value is _null_, if the [feature] is _null_.
     *
     * The method recognized manually set features, and re-calculates the JSON for them!
     * @since 3.0
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
     * @since 3.0
     */
    open fun newFeature(): NakshaFeature? = tuple?.toNakshaFeature()
}
@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.model.*
import naksha.model.objects.NakshaFeature
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * A [tuple][Tuple] as part of some result or intermediate thread local processing.
 *
 * Note that result-tuple are not thread safe, they are helpful to cache individual states of [NakshaFeature] instances, and when there is only an optional demand to physically read all the underlying data. So, a result-tuple basically means that there is a tuple being part of a result-set, but it might not yet have been loaded into memory.
 *
 * Assume for example, there are 500,000 tuples part of a transaction, it is most often not useful to load all of them into memory, but we need at least the meta-information about all of them, so that they are part of the result-set, then normally a process steps through the result-set and stops, when enough have been processed.
 */
@JsExport
open class ResultTuple(
    /**
     * Reference to storage from which the row was received.
     */
    @JvmField val storage: IStorage,

    /**
     * The tuple-number.
     */
    @JvmField val tupleNumber: TupleNumber,

    /**
     * The operation that was executed.
     */
    @JvmField var op: ExecutedOp,

    /**
     * The feature-id.
     *
     * Can be _null_, when not yet fetched from the storage, use [IStorage.fetchTuples].
     *
     * When ordering by feature-id, the storage should load the feature identifiers together with the row identifiers. This operation will be slower than loading only the row identifiers, but still fast enough. However, at many places it is needed, like to create seekable views.
     */
    @JvmField var featureId: String?,

    /**
     * If the row is already in the cache, the reference to the row.
     *
     * Can be _null_, when not yet fetched from the storage, use [IStorage.fetchTuples] or when [op] is [PURGED][ExecutedOp.PURGED] or [RETAINED][ExecutedOp.RETAINED].
     */
    @JvmField var tuple: Tuple?
) {

    /**
     * The map-id of the map in which the row is located.
     */
    val mapId: String?
        get() = storage.getMapId(tupleNumber.mapNumber())

    /**
     * The collection-id of the collection in which the row is located.
     */
    val collectionId: String?
        get() {
            val mapId = mapId
            return if (mapId != null) storage[mapId].getCollectionId(tupleNumber.collectionNumber()) else null
        }

    /**
     * Returns the feature-id, if it is already known.
     *
     * First reads the most reliable ID from the [tuple], if the row is not yet fetched, tries the [featureId] property.
     * @return the feature-id, if available.
     */
    fun id() : String? = tuple?.meta?.id ?: featureId

    /**
     * Convert the row into a feature, and cache the feature.
     *
     * **Beware**: If the returned feature is modified, this will as well modify the cached version in the row.
     * @return the row converted into a feature, cached in this result-row.
     */
    var feature: NakshaFeature? = null
        get() {
            if (field == null) {
                if (tuple == null) NakshaCache.tupleCache(storage.id)[tupleNumber]
                if (tuple != null) field = tuple?.toNakshaFeature()
            }
            return field
        }

    /**
     * Convert the row into a feature, bypass the cache.
     *
     * @return a new copy of the row converted into a feature.
     */
    fun newFeature(): NakshaFeature? = tuple?.toNakshaFeature()
}
@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.PAnyMap
import naksha.base.AnyObjectList
import naksha.model.*
import naksha.model.objects.NakshaCatalogList
import naksha.model.objects.NakshaCollectionList
import naksha.model.objects.NakshaFeatureList
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmOverloads

/**
 * Success response, means all operations succeeded, and it's safe to commit the transaction.
 *
 * Some stores are going to directly return [objects], others will provide a [resultSet]. The most easy usage is:
 * ```kotlin
 * val r = session.execute(request)
 * if (r is SuccessResponse) {
 *   val features = r.loadObjects().asFeatures
 * } else {
 *   // ErrorResponse
 * }
 * ```
 *
 * @since 3.0
 */
@JsExport
open class SuccessResponse : Response() {
    /**
     * The amount of objects being in the response.
     * @since 3.0
     */
    override val length: Int
        get() = objects?.size ?: resultSet?.size ?: 0

    /**
     * The result-set as returned by the storage; if any.
     *
     * The result-set can be `null`, if the storage directly returns the result as [objects]. This property is **not** serializable!
     *
     * If a result-set is available, the client may decide to only decode a subset of the [objects]. A result-set means that data acquisition is done in two phases. The first only returns the [TupleNumber][naksha.base.TupleNumber] of the result, which then have to be used in a second step to load the [Tuple] and decode it into the [object][PAnyMap].
     *
     * There are plenty of advantages of this mechanism. For example, when two queries produce overlapping results, the overlapping part is only loaded ones due to caching. When the same query is executed multiple times, with no, or only minor change between the queries, only those objects that have changed between the two queries need to be loaded. The split allows to serialize the result-set into tuple-numbers, then to return only the tuple-numbers to the client. This allows the client to decide which objects he knows already, and which to load fully. So, the two phase approch is especially helpful for proxies.
     *
     * Apart from these obvious advantages, the result-set based upon [TupleNumber][naksha.base.TupleNumber] allows a better post-filtering. It allows the client to load all results _(which are first only the [TupleNumber][naksha.base.TupleNumber])_, then to iterate the result in chunks and to post-filter the result, stopping when enough objects are found. Assume 100,000 features have been part of the result-set, but only the first 100 with property `foo` being `Bar` are needed. Then the client could process the result-set in chunks of 1000'th, potentially only needing to read the first 1000 results to be able to generate the desired 100 results _(this avoids loading 99,000 objects from the storage!)_.
     *
     * **Dependent on the use-case, the two phase query can be more or less useful.**
     *
     * @since 3.0
     * @see loadObjects
     */
    var resultSet: ResultSet? = null

    /**
     * @see resultSet
     */
    fun withResultSet(resultSet: ResultSet?): SuccessResponse {
        this.resultSet = resultSet
        return this
    }

    /**
     * Clears the [resultSet].
     * @see resultSet
     */
    fun withoutResultSet(): SuccessResponse {
        this.resultSet = null
        return this
    }

    /**
     * The raw [objects][naksha.base.PAnyMap] being part of the result-set.
     *
     * Can be `null`, in which case [loadObjects] should be called to populate it. Alternatively [asObjects], [asFeatures], [asCatalogs], or [asCollections] can be called, which will ensure that a potential [resultSet] is loaded and converted into [objects][PAnyMap].
     *
     * ### Beware
     * The [resultSet] is optional, not all storages will support it, while [objects] are mandatory for all storages. The [resultSet] is rather a low-level form of returning results. It allows a controlled two phase data query. All storages that support [resultSet] will automatically participate in a shared internal object cache, potentially supporting as well external caching configured by the using application.
     * @since 3.0
     */
    open var objects: AnyObjectList?
        get() = getAs("features", AnyObjectList::class)
        set(value) {
            if (value == null) {
                remove("features")
            } else {
                setRaw("features", value)
            }
        }

    /**
     * Copy given [objects][PAnyMap] into [objects].
     * @param results the objects that form the success response.
     * @return this.
     * @since 3.0
     * @see [objects]
     */
    open fun <OBJECT : PAnyMap> withObjects(vararg results: OBJECT?): SuccessResponse {
        val list = AnyObjectList()
        list.setCapacity(results.size)
        list.addAll(results)
        this.objects = list
        return this
    }

    /**
     * Copy the given list of [PAnyMap] into the result [objects] .
     * @param results the objects that form the success response.
     * @return this.
     * @since 3.0
     * @see [objects]
     */
    @JsName("withListOfAnyObjects")
    open fun <OBJECT: PAnyMap, LIST: List<OBJECT?>> withObjects(results: LIST): SuccessResponse {
        val list = AnyObjectList()
        list.setCapacity(results.size)
        list.addAll(results)
        this.objects = list
        return this
    }

    /**
     * Assign or copy the given [AnyObjectList] to [objects].
     * @param results the objects that form the success response.
     * @param copy if `true`, copies the given object list; defaults to `false`.
     * @return this.
     * @since 3.0
     * @see [objects]
     */
    @JsName("withAnyObjectList")
    @JvmOverloads
    open fun withObjects(results: AnyObjectList, copy: Boolean = false): SuccessResponse {
        val list: AnyObjectList
        if (copy) {
            list = AnyObjectList()
            list.setCapacity(results.size)
            list.addAll(results)
        } else {
            list = results
        }
        this.objects = list
        return this
    }

    /**
     * If [objects] is `null` and only then, this method loads [objects] from the [resultSet], if a result-set exists.
     *
     * If there are already [objects], the method does nothing. After calling the method [objects] will not be `null` anymore. The method will not clear the [resultSet], so it stays available. If that is not wished, the method call should be chained with a following [withoutResultSet] call, like:
     * ```kotlin
     * var features = rs.loadObjects()
     *     .withoutResultSet()
     *     .asFeatures
     * ```
     * @param from the index of the first result to load.
     * @param to the index of the first result to **not** load.
     * @param limit the maximal amount of objects needed, only relevant when filtering all results.
     * @param tupleFilter a pre-filter to apply to filter tuples, before even decoding objects; not all storages do support this, so it may be skipped!
     * @param objectFilter the filter to apply to all objets bypassing the `tupleFilter` _(which is true for all objects, when the storage does not support tuple)_, when loading from [resultSet].
     * @return this.
     * @since 3.0
     * @see withoutResultSet
     */
    @JvmOverloads
    fun loadObjects(from: Int = 0, to: Int = length, limit: Int = Int.MAX_VALUE, tupleFilter: ITupleFilter? = null, objectFilter: IObjectFilter? = null): SuccessResponse {
        val objects = this.objects
        if (objects != null) return this
        this.objects = resultSet?.getObjects(from, to, limit, tupleFilter, objectFilter) ?: AnyObjectList()
        return this
    }

    /**
     * Convert the [objects] into a [AnyObjectList], optionally loading objects from the result-set, when not already done.
     * @since 3.0
     * @see objects
     */
    val asObjects: AnyObjectList
        get() {
            loadObjects()
            return objects!!
        }

    /**
     * Convert the [objects] into a [NakshaFeatureList], optionally loading objects from the result-set, when not already done.
     * @since 3.0
     * @see objects
     */
    val asFeatures: NakshaFeatureList
        get() = asObjects.proxy(NakshaFeatureList::class)

    /**
     * Convert the [objects] into a [NakshaCollectionList], optionally loading objects from the result-set, when not already done.
     * @since 3.0
     * @see objects
     */
    val asCollections: NakshaCollectionList
        get() = asObjects.proxy(NakshaCollectionList::class)

    /**
     * Convert the [objects] into a [NakshaCatalogList], optionally loading objects from the result-set, when not already done.
     * @since 3.0
     * @see objects
     */
    val asCatalogs: NakshaCatalogList
        get() = asObjects.proxy(NakshaCatalogList::class)
}
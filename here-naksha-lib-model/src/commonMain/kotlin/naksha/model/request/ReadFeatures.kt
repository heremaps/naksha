@file:Suppress("OPT_IN_USAGE", "MemberVisibilityCanBePrivate")

package naksha.model.request

import naksha.base.*
import naksha.model.GuidList
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Read features from a collection of a map of a storage.
 *
 * If a logical **OR** between the different condition is needed, for example search for features being in a certain bounding box **or** having a certain tag, then two read-requests should be executed, and joined by the client. These queries can be executed in parallel using two distinct sessions to improve the performance.
 *
 * Read requests by default return full [rows][naksha.model.Row], but allows to unselect some parts via [returnColumns]. Beware, that there is no guarantee what the storage will do, but it may improve the performance.
 *
 * @since 3.0.0
 */
@JsExport
open class ReadFeatures() : ReadRequest() {

    /**
     * Create a new read-features request for the given collections.
     * @param collectionIds the collection identifiers.
     * @since 3.0.0
     */
    @JsName("forCollections")
    constructor(vararg collectionIds: String) : this() {
        this.collectionIds.addAll(collectionIds)
    }

    companion object ReadFeatures_C {
        private val STRING_NULL = NullableProperty<ReadRequest, String>(String::class)
        private val BOOLEAN = NotNullProperty<ReadRequest, Boolean>(Boolean::class) { _, _ -> false }
        private val INT_1 = NotNullProperty<ReadRequest, Int>(Int::class) { _, _ -> 1 }
        private val INT64_NULL = NullableProperty<ReadRequest, Int64>(Int64::class)
        private val STRING_LIST = NotNullProperty<ReadRequest, StringList>(StringList::class)
        private val ORDER_BY_NULL = NullableProperty<ReadRequest, OrderBy>(OrderBy::class)
        private val GUID_LIST = NotNullProperty<ReadRequest, GuidList>(GuidList::class)
        private val QUERY = NotNullProperty<ReadRequest, RequestQuery>(RequestQuery::class)
    }

    /**
     * The map from which to read.
     *
     * If being an empty string, the default map is read, if being _null_, the [map from the context][naksha.model.NakshaContext.map] should be queried.
     * @since 3.0.0
     */
    var map by STRING_NULL

    /**
     * The collections to query, should not be left empty.
     * @since 3.0.0
     */
    var collectionIds by STRING_LIST

    /**
     * Extend the request to search through deleted features.
     */
    var queryDeleted by BOOLEAN

    /**
     * Extend the request to search through historic states of features.
     */
    var queryHistory by BOOLEAN

    /**
     * Defines how many rows (versions) of each matching feature should be returned.
     *
     * The defaults to 1, which means only the latest version, being closest to the given maximal [version] should be returned, if no [version] given, the latest version is meant.
     *
     * If multiple versions are requested, the execution may become drastically slower, therefore this feature should be used with care!
     * @since 3.0.0
     */
    var versions by INT_1

    /**
     * Limit the read to all rows with the given minimal version, _null_ if no limit.
     * @since 3.0.0
     */
    var minVersion by INT64_NULL

    /**
     * Limit the read to all features with a maximal version, _null_ if no limit. This effectively is a request for a specific version, if no [minVersion] is set.
     * @since 3.0.0
     */
    var version by INT64_NULL

    /**
     * Order the result-set like given; this is an expensive operation and should be avoided.
     *
     * If an order is required, but no specific one, then it is strongly recommended to stick with the [deterministic order][OrderBy.deterministic], which is produced by creating a blank empty [OrderBy] object or through the static helper method [OrderBy.deterministic]. Ordering by anything else can have a drastic performance impact.
     *
     * Note that the [deterministic ordering][OrderBy.deterministic] will always be selected implicitly by the storage, when a [_handle_ is requested][returnHandle], and no explicit [orderBy] was provided. Creating a _handle_ always need an order, to be able to seek in the result-set.
     */
    var orderBy by ORDER_BY_NULL

    /**
     * Add all features that match the given IDs into the result-set.
     * @since 3.0.0
     */
    var featureIds by STRING_LIST

    /**
     * Add all features that match the given [GUIDs][naksha.model.Guid] into the result-set.
     *
     * This can be used to load features in specific states.
     * @since 3.0.0
     */
    var guids by GUID_LIST

    /**
     * Add all features that match the given query into the result-set.
     * @since 3.0.0
     */
    var query by QUERY
}
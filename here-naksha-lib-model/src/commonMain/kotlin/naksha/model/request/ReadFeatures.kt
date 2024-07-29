@file:Suppress("OPT_IN_USAGE", "MemberVisibilityCanBePrivate")

package naksha.model.request

import naksha.base.*
import naksha.geo.HereTile
import naksha.model.request.query.*
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Read features from a map of a storage.
 *
 * The request has certain limits which apply as logical AND condition. So, the request only returns features that match **ALL** given queries. Therefore, only if [queryProperties], [spatial], [tags] **and** [refTiles] do match for a feature, this feature is returned. If any of the queries should not be applied, they must be _null_ (which is the default).
 *
 * If an **OR** condition is needed, for example search for features being in a certain bounding box **or** having a certain tag, then two read-requests should be executed and joined by the client. These queries can be executed in parallel using two distinct sessions to improve the speed.
 *
 * If executed against the virtual collection `naksha~collections`, it can be used as well to perform complex queries against collections.
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
        private val INT_LIST = NotNullProperty<ReadRequest, IntList>(IntList::class)
        private val STRING_LIST = NotNullProperty<ReadRequest, StringList>(StringList::class)
        private val SPATIAL_QUERY_NULL = NullableProperty<ReadRequest, ISpatialQuery>(ISpatialQuery::class)
        private val TAG_QUERY_NULL = NullableProperty<ReadRequest, ITagQuery>(ITagQuery::class)
        private val PROPERTIES_QUERY_NULL = NullableProperty<ReadRequest, IPropertyQuery>(IPropertyQuery::class)
        private val ORDER_BY_NULL = NullableProperty<ReadRequest, OrderBy>(OrderBy::class)
    }

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
     * Order the results, this is an expensive operation and should be avoided.
     *
     * If ordering is required, then it is strongly recommended to stick with [SortOrder.ANY]. Ordering by anything else can have a drastic performance impact.
     */
    var orderBy by ORDER_BY_NULL

    /**
     * Limit the result-set to features that match the given IDs.
     *
     * If the list is empty, no limit is applied.
     * @since 3.0.0
     */
    var featureIds by STRING_LIST

    /**
     * Limit the result-set to all features that match the given spatial query.
     * @since 3.0.0
     */
    var spatial by SPATIAL_QUERY_NULL

    /**
     * Limit the result-set to all features that match the given tag query.
     * @since 3.0.0
     */
    var tags by TAG_QUERY_NULL

    /**
     * Limit the result-set to all features that match the given property query.
     * @since 3.0.0
     */
    var properties by PROPERTIES_QUERY_NULL

    /**
     * Limit the result-set to all features that have a reference point in one of the given tiles.
     *
     * If the list is empty, no limit is applied.
     * @since 3.0.0
     */
    var refTiles by INT_LIST

    /**
     * Adds the given tile to the list of tiles to query for reference points, so updating [refTiles].
     * @param tile the tile to search in.
     * @return this.
     * @since 3.0.0
     */
    fun addRefTile(tile: HereTile): ReadFeatures {
        refTiles.add(tile.intKey)
        return this
    }

    /**
     * Removes the given tile from the list of tiles to query for reference points, so updating [refTiles].
     * @param tile the tile no longer search in.
     * @return this.
     * @since 3.0.0
     */
    fun removeRefTile(tile: HereTile): ReadFeatures {
        refTiles.remove(tile.intKey)
        return this
    }
}
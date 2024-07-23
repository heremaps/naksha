@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.Int64
import naksha.base.minus
import naksha.geo.HereTile
import naksha.model.Guid
import naksha.model.request.condition.*
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * A read request to fetch features.
 *
 * The request has certain limits which apply as logical AND condition. So, the request only returns features that match **ALL** given queries. Therefore, only if [queryProperties], [querySpatial], [queryTags] **and** [queryRefTiles] do match for a feature, this feature is returned. If any of the queries should not be applied, they must be _null_ (which is the default).
 *
 * If an **OR** condition is needed, for example search for features being in a certain bounding box **or** having a certain tag, then two read-requests should be executed and joined by the client. These queries can be executed in parallel using two distinct sessions to improve the speed.
 *
 * If executed against the virtual collection `naksha~collections`, it can be used as well to perform complex queries against collections.
 */
@JsExport
class ReadFeatures : ReadRequest<ReadFeatures>() {

    /**
     * Extend the request to search through deleted features.
     */
    @JvmField
    var queryDeleted: Boolean = false

    fun withQueryDeleted(): ReadFeatures {
        queryDeleted = true
        return this
    }

    /**
     * Extend the request to search through historic states of features.
     */
    @JvmField
    var queryHistory: Boolean = false

    fun withQueryHistory(): ReadFeatures {
        queryHistory = true
        return this
    }

    /**
     * Defines how many versions of each matching feature should be returned, defaults to 1, which means only the version being closest to the given [maxVersion], if no [maxVersion] given, the latest version is meant.
     *
     * If multiple versions are requested, the execution becomes drastically slower, therefore this feature should be used with care.
     */
    @JvmField
    var versions: Int = 1

    /**
     * Change how many versions of each matching feature should be returned, see [versions] for more details.
     * @param versions the amount of versions to return.
     * @return this.
     */
    fun withLimitVersions(versions: Int): ReadFeatures {
        this.versions = versions
        return this
    }

    /**
     * Limit the read to all features with a minimal version, _null_ if no limit.
     */
    @JvmField
    var minVersion: Int64? = null

    /**
     * Limit the read to all features with a minimal version, _null_ if no limit.
     * @param minVersion the minimal version, _null_ if no minimum.
     * @return this.
     */
    fun withMinVersion(minVersion: Int64?): ReadFeatures {
        this.minVersion = minVersion
        return this
    }

    /**
     * Limit the read to all features with a maximal version, _null_ if no limit.
     */
    @JvmField
    var maxVersion: Int64? = null

    /**
     * Limit the read to all features with a maximal version, _null_ if no limit.
     * @param maxVersion the maximal version; _null_ if no maximum.
     * @return this.
     */
    fun withMaxVersion(maxVersion: Int64?): ReadFeatures {
        this.maxVersion = maxVersion
        return this
    }

    /**
     * Order the results, this is an expensive operation and should be avoided.
     */
    @JvmField
    var orderBy: OrderBy? = null

    /**
     * Change the result order, this is an expensive operation and should be avoided.
     * @param orderBy the order to ensure, _null_ if no specific order needed.
     * @return this.
     */
    fun withOrderBy(orderBy: OrderBy?): ReadFeatures {
        this.orderBy = orderBy
        return this
    }

    /**
     * The collections to query, should not be left empty.
     */
    @JvmField
    var collectionIds: MutableList<String> = mutableListOf()

    /**
     * Add the given collection-id to the list of collections to query.
     * @param id the collection-id to add.
     * @return this.
     */
    fun addCollectionId(id: String): ReadFeatures {
        if (!collectionIds.contains(id)) collectionIds.add(id)
        return this
    }

    /**
     * A list of feature IDs to limit the query to, with the key being the feature ID, and the value being the optional states to limit the query to.
     *
     * **Warning**: If the map exists and is empty, no features will ever be returned!
     */
    @JvmField
    var queryIds: MutableMap<String, MutableList<Guid?>?>? = null

    /**
     * Sets the list of features to limit the query to.
     * @param queryIds the map to set.
     * @return this.
     */
    fun withQueryIds(queryIds: MutableMap<String, MutableList<Guid?>?>?): ReadFeatures {
        this.queryIds = queryIds
        return this
    }

    /**
     * Returns the query IDs map, if it does not yet exist, creates it.
     * @return the query IDs map.
     */
    fun useQueryIds(): MutableMap<String, MutableList<Guid?>?> {
        var queryIds = this.queryIds
        if (queryIds == null) {
            queryIds = mutableMapOf()
            this.queryIds = queryIds
        }
        return queryIds
    }

    /**
     * Add the given feature IDs to the query limits, if the IDs are already part of the query limit, the ID is skipped.
     * @param ids the IDs to add.
     * @return this.
     */
    fun addIds(vararg ids: String): ReadFeatures {
        val queryIds = useQueryIds()
        for (id in ids) {
            if (!queryIds.contains(id)) queryIds[id] = null
        }
        return this
    }

    /**
     * Limit the query to the feature in the given state. If no state is given, the method only ensures that the feature-id is part of the limit.
     *
     * **Note**:
     * - If a state is given and the feature is already part of the request, then the state is added to the list of allowed states.
     * - If no state is given and the feature is already part of the request, then the existing state list left untouched.
     */
    fun addId(id: String, state: Guid? = null): ReadFeatures {
        val queryIds = useQueryIds()
        val existing = queryIds[id]
        if (existing == null) {
            queryIds[id] = null
        } else if (state != null && !existing.contains(state)) {
            existing.add(state)
        }
        return this
    }

    /**
     * Limit the read to all features that match the given spatial query.
     */
    @JvmField
    var querySpatial: IQuery<SpatialOuery>? = null

    /**
     * Defines a spatial query to filter.
     * @param query the spatial query or a logical combination of spatial queries.
     * @return this.
     */
    fun withQuerySpatial(query: IQuery<SpatialOuery>?): ReadFeatures {
        querySpatial = query
        return this
    }

    /**
     * Limit the read to all features that match the given spatial query.
     */
    @JvmField
    var queryTags: IQuery<TagQuery>? = null

    /**
     * Defines a tag query to filter.
     * @param query the tag query or a logical combination of tag queries.
     * @return this.
     */
    fun withQueryTags(query: IQuery<TagQuery>?): ReadFeatures {
        queryTags = query
        return this
    }

    /**
     * Limit the read to all features that match the given property query.
     */
    @JvmField
    var queryProperties: IQuery<Query>? = null

    /**
     * Defines a property query to filter.
     * @param query the property query or a logical combination of property queries.
     * @return this.
     */
    fun withQueryProperties(query: IQuery<Query>?): ReadFeatures {
        queryProperties = query
        return this
    }

    /**
     * Limit the read against all features that have a reference point in one of the given tiles.
     *
     * **Warning**: If an empty array is given, the result will be empty!
     */
    @JvmField
    var queryRefTiles: Array<HereTile>? = null

    /**
     * Adds the given tile into the list of tiles to query, actually only returning features that have their reference point in the given tile or any of the other specified tiles.
     * @param tile the tile to search into.
     * @return this.
     */
    fun addRefTile(tile: HereTile): ReadFeatures {
        val q = this.queryRefTiles
        if (q == null) {
            this.queryRefTiles = arrayOf(tile)
        } else {
            this.queryRefTiles = q + tile
        }
        return this
    }

    /**
     * Adds the given tile into the list of tiles to query, actually only returning features that have their reference point in the given tile or any of the other specified tiles.
     * @param tile the tile to search into.
     * @return this.
     */
    fun removeRefTile(tile: HereTile): ReadFeatures {
        val source = this.queryRefTiles ?: return this
        val i = source.indexOf(tile)
        if (i < 0) return this
        if (source.size == 1) {
            this.queryRefTiles = emptyArray()
        } else {
            this.queryRefTiles = source - tile
        }
        return this
    }

    override fun copyTo(copy: ReadFeatures): ReadFeatures {
        super.copyTo(copy)
        copy.queryDeleted = this.queryDeleted
        copy.queryHistory = this.queryHistory
        copy.versions = versions
        copy.minVersion = minVersion
        copy.maxVersion = maxVersion
        // TODO: Make a deep copy!
        copy.orderBy = this.orderBy
        copy.collectionIds = this.collectionIds
        copy.queryIds = this.queryIds
        copy.querySpatial = this.querySpatial
        copy.queryTags = this.queryTags
        copy.queryProperties = this.queryProperties
        copy.queryRefTiles = this.queryRefTiles
        return copy
    }

    companion object ReadFeaturesCompanion {
        /**
         * Returns a query that reads only the IDs of features, requires to set some query.
         * @param collectionId the collection to read.
         * @return the prepared read-features query, must be extended by any query condition.
         */
        @JvmStatic
        @JsStatic
        fun readIdsOnly(collectionId: String) = ReadFeatures()
            .addCollectionId(collectionId)
            .withRowOptions(RowOptions().withNoFeature().withNoGeometry().withNoMeta().withNoTags())

        /**
         * Returns a query that reads a single feature.
         * @param collectionId the collection to query.
         * @param featureId the feature-id to query.
         * @return the ready to execute read-features.
         */
        @JvmStatic
        @JsStatic
        fun readFeatureById(collectionId: String, featureId: String) = ReadFeatures()
            .addCollectionId(collectionId)
            .withQueryProperties(Query(Property.id(), QueryString.EQUALS, featureId))

        /**
         * Returns a query that reads multiple feature by ID.
         * @param collectionId the collection to query.
         * @param featureIds the feature-ids to query.
         * @return the ready to execute read-features.
         */
        @JvmStatic
        @JsStatic
        fun readFeaturesById(collectionId: String, vararg featureIds: String) = ReadFeatures()
            .addCollectionId(collectionId)
            .withQueryProperties(Query(Property.id(), QueryOp.IS_ANY_OF, arrayOf(*featureIds)))
    }
}
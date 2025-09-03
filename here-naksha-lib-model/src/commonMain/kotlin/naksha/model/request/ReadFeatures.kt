@file:Suppress("OPT_IN_USAGE", "MemberVisibilityCanBePrivate")

package naksha.model.request

import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.StringList
import naksha.model.GuidList
import naksha.model.Version
import naksha.model.request.query.IPropertyQuery
import naksha.model.request.query.ITagQuery
import kotlin.js.JsExport

/**
 * Read features from a collection of a map of a storage.
 *
 * If a logical **OR** between the different condition is needed, for example search for features being in a certain bounding box **or** having a certain tag, then two read-requests should be executed, and joined by the client. These queries can be executed in parallel using two distinct sessions to improve the performance.
 *
 * @since 3.0.0
 */
@JsExport
open class ReadFeatures : ReadRequest() {

    companion object ReadFeatures_C {
        private val STRING_OR_NULL = NullableProperty<ReadRequest, String>(String::class)
        private val BOOLEAN_OR_FALSE =
            NotNullProperty<ReadRequest, Boolean>(Boolean::class) { _, _ -> false }
        private val BOOLEAN_OR_TRUE =
            NotNullProperty<ReadRequest, Boolean>(Boolean::class) { _, _ -> true }
        private val INT_OR_1 = NotNullProperty<ReadRequest, Int>(Int::class) { _, _ -> 1 }
        private val VERSION_OR_NULL = NullableProperty<ReadRequest, Version>(Version::class)
        private val STRING_LIST = NotNullProperty<ReadRequest, StringList>(StringList::class)
        private val ORDER_BY_OR_NULL = NullableProperty<ReadRequest, OrderBy>(OrderBy::class)
        private val GUID_LIST = NotNullProperty<ReadRequest, GuidList>(GuidList::class)
        private val QUERY = NotNullProperty<ReadRequest, RequestQuery>(RequestQuery::class)
    }

    /**
     * The id of the map from which to read.
     *
     * @since 3.0
     */
    var mapId by STRING_OR_NULL

    /**
     * @see [mapId]
     */
    open fun withMapId(value: String?): ReadFeatures {
        mapId = value
        return this
    }

    /**
     * Sets the property query for the request and automatically manages the required PropertyFilter.
     * If the provided query is null, any existing PropertyFilter will be removed.
     *
     * @param pQuery The property query to apply, or null to clear it.
     * @return this.
     *
     * @since 3.0
     */
    open fun withPropertyQuery(pQuery: IPropertyQuery?): ReadFeatures {
        this.query.properties = pQuery
        this.resultFilters.removeAll { it is PropertyFilter }

        if (pQuery != null) {
            this.resultFilters.add(PropertyFilter(this))
        }

        return this
    }

    /**
     * Refreshes PropertyFilter based on [RequestQuery.properties] found under [ReadFeatures.query]
     * This method comes handy if [IPropertyQuery] was mutated outside of this class scope,
     * in such cases we need to populate the filter once again so it will be in sync with the query
     */
    fun refreshPropertyFilter() {
        this.resultFilters.removeAll { it is PropertyFilter }
        if(query.properties != null) {
            this.resultFilters.add(PropertyFilter(this))
        }
    }

    /**
     * Sets the tag query for the request.
     * If the provided query is null, the tag condition will be cleared.
     *
     * @param tQuery The tag query to apply, or null to clear it.
     * @return this.
     *
     * @since 3.0
     */
    open fun withTagQuery(tQuery: ITagQuery?): ReadFeatures {
        this.query.tags = tQuery
        return this
    }

    /**
     * Ids of collections to read.
     * @since 3.0
     */
    var collectionIds by STRING_LIST

    /**
     * Adds the given collection-id into [collectionIds], if it is not already in it.
     * @param collectionId the collection-id to add.
     * @return this.
     * @since 3.0
     */
    open fun addCollectionId(collectionId: String?): ReadFeatures {
        if (!collectionIds.contains(collectionId)) collectionIds.add(collectionId)
        return this
    }

    /**
     * Adds the given collection-ids into [collectionIds], if it is not already in it.
     * @param collectionIds the collection-ids to add.
     * @return this.
     * @since 3.0
     */
    open fun addCollectionIds(vararg collectionIds: String): ReadFeatures {
        val ids = this.collectionIds
        @Suppress("SENSELESS_COMPARISON")
        if (collectionIds != null && collectionIds.isNotEmpty()) {
            for (id in collectionIds) if (!ids.contains(id)) ids.add(id)
        }
        return this
    }

    /**
     * Extend the request to search through lately deleted features _(defaults to `false`)_.
     *
     * Actually, unless explicitly disabled, deleted features are stored in a shadow table, this information is used in views, so that a feature being deleted in a higher level layer, can be removed from the view, rather than to show their deleted counterpart read from a lower level layer. In other words, `lib-view` will always enable this, and won't work correctly, unless the deleted features are available.
     *
     * ### Note
     * This option is totally distinct form [queryHistory], and ignored ones [queryHistory] is `true`. The reason the two switches behave differently is, that entries in the _shadow_ table can be deleted, while the history is really immutable. This is important to rollback a delete (restore the original shadowed state), what is exactly what the [PURGE][WriteOp.PURGE] write operation is good for.
     */
    var queryDeleted by BOOLEAN_OR_FALSE

    /**
     * Extend the request to search through historic states of features _(defaults to `false`)_.
     *
     * Setting this to `true` will cause deleted states to be returned as well. When the history is queried without specifying any specific ordering, and [versions] is bigger than `1`, then the results shall be ordered automatically by the storage in reverse of their `version`, so the latest [Tuple][naksha.model.Tuple] should be the first one returned.
     */
    var queryHistory by BOOLEAN_OR_FALSE

    /**
     * Defines how many rows (versions) of each matching feature should be returned.
     *
     * The defaults to 1, which means only the latest version, being closest to the given maximal [version] should be returned, if no [version] given, the latest version is meant.
     *
     * This parameter is ignored for queries to a [Guid][naksha.model.Guid], because a [Guid][naksha.model.Guid] already identifies an exact version. The query requires that [queryHistory] is `true`.
     *
     * If set to anything not being `1` _(the default)_ and [queryHistory] is `false`, the request will be rejected with [ILLEGAL_ARGUMENT][naksha.model.NakshaError.ILLEGAL_ARGUMENT].
     *
     * If multiple versions are requested, the execution may become drastically slower, therefore this feature should be used with care!
     * @since 3.0.0
     */
    var versions by INT_OR_1

    /**
     * Limit the read to all rows with the given minimal version, `null` if no limit.
     *
     * If set to anything not being `null` _(the default)_ and [queryHistory] is `false`, the request will be rejected with [ILLEGAL_ARGUMENT][naksha.model.NakshaError.ILLEGAL_ARGUMENT].
     * @since 3.0.0
     */
    var minVersion by VERSION_OR_NULL

    /**
     * Limit the read to all features with a specific maximum version, `null` if no limit _(latest/HEAD version)_.
     *
     * This effectively is a request for a specific version, if no [minVersion] is set, and [versions] is default or explicitly `1`.
     *
     * If set to anything not being `null` _(the default)_ and [queryHistory] is `false`, the request will be rejected with [ILLEGAL_ARGUMENT][naksha.model.NakshaError.ILLEGAL_ARGUMENT].
     * @since 3.0.0
     */
    var version by VERSION_OR_NULL

    /**
     * Order the result-set like given; this is an expensive operation and should be avoided.
     *
     * If an order is required, but no specific one, then it is strongly recommended to stick with the [deterministic order][OrderBy.deterministic], which is produced by creating a blank empty [OrderBy] object or through the static helper method [OrderBy.deterministic]. Ordering by anything else can have a drastic performance impact.
     */
    var orderBy by ORDER_BY_OR_NULL

    /**
     * Add all features that match the given IDs into the result-set.
     * @since 3.0.0
     */
    //TODO CASL-1149 should support custom queries
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

    /**
     * Tests whether this request is effectively a query for all features in _HEAD/latest_ state, so it has no actual conditions, does only request one version of each feature, and does not touch history or deletion table.
     *
     * The method ignores optionally provided [ordering][orderBy].
     * @return _true_ if the query is effectively reading of all _HEAD/latest_ states of all features; _false_ otherwise.
     */
    fun isReadAllFromHead(): Boolean = minVersion == null
            && !queryDeleted
            && !queryHistory
            && versions == 1
            && version == null
            && featureIds.isEmpty()
            && guids.isEmpty()
            && query.hasNoConditions()
}
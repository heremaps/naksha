@file:Suppress("OPT_IN_USAGE", "MemberVisibilityCanBePrivate")

package naksha.model.request

import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.StringList
import naksha.model.GuidList
import naksha.model.Version
import naksha.model.request.ops.Op
import naksha.model.request.query.IMemberQuery
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
        private val STRING_LIST = NotNullProperty<ReadRequest, StringList>(StringList::class) { _, _ -> StringList() }
        private val BOOLEAN_OR_FALSE = NotNullProperty<ReadRequest, Boolean>(Boolean::class) { _, _ -> false }
        private val INT_OR_1 = NotNullProperty<ReadRequest, Int>(Int::class) { _, _ -> 1 }
        private val VERSION_OR_NULL = NullableProperty<ReadRequest, Version>(Version::class)
        private val ORDER_BY_OR_NULL = NullableProperty<ReadRequest, OrderBy>(OrderBy::class)
        private val GUID_LIST = NotNullProperty<ReadRequest, GuidList>(GuidList::class)
        private val QUERY = NotNullProperty<ReadRequest, RequestQuery>(RequestQuery::class)
        private val OP_OR_NULL = NullableProperty<ReadRequest, Op>(Op::class)
    }

    /**
     * The id of the catalog from which to read.
     *
     * @since 3.0
     */
    var catalogId by STRING_OR_NULL

    /**
     * @see [catalogId]
     */
    open fun withCatalogId(value: String?): ReadFeatures {
        catalogId = value
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
    @Deprecated("Replaced with op", replaceWith = ReplaceWith("op"))
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
    @Deprecated("Replaced with op", replaceWith = ReplaceWith("op"))
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
    @Deprecated("Replaced with op", replaceWith = ReplaceWith("op"))
    open fun withTagQuery(tQuery: ITagQuery?): ReadFeatures {
        this.query.tags = tQuery
        return this
    }

    /**
     * Ids of collections to read.
     * @since 3.0
     */
    var collectionId: String? by STRING_OR_NULL

    /**
     * Sets the collection-id into [collectionId].
     * @param collectionId the collection-id to set.
     * @return this.
     * @since 3.0
     */
    open fun withCollectionId(collectionId: String?): ReadFeatures {
        this.collectionId = collectionId
        return this
    }

    /**
     * Extend the request to include features that are in a deleted state _(defaults to `false`)_.
     */
    var queryDeleted: Boolean by BOOLEAN_OR_FALSE

    /**
     * Extend the request to search through historic states of features _(defaults to `false`)_.
     *
     * Setting this to `true` adds past states from the **HISTORY** section to the result set. When
     * [versions] is greater than `1`, results are ordered automatically by the storage in reverse
     * version order, so the most recent state is returned first.
     */
    var queryHistory: Boolean by BOOLEAN_OR_FALSE

    /**
     * Defines how many states (versions) of each matching feature should be returned _(defaults to `1`)_.
     *
     * A value of `1` means only the single latest state closest to the given maximal [version] is
     * returned; if no [version] is given, the current HEAD state is meant.
     *
     * This parameter is ignored for queries by [Guid][naksha.model.Guid], because a
     * [Guid][naksha.model.Guid] already identifies an exact state. The parameter requires
     * [queryHistory] to be `true`.
     *
     * If set to anything other than `1` _(the default)_ while [queryHistory] is `false`, the request
     * will be rejected with [ILLEGAL_ARGUMENT][naksha.model.NakshaError.ILLEGAL_ARGUMENT].
     *
     * Requesting multiple versions can have a significant performance impact and should be used with care.
     * @since 3.0.0
     */
    var versions: Int by INT_OR_1

    /**
     * Limit the read to all states at or after the given minimum version, `null` if no limit.
     *
     * If set to anything other than `null` _(the default)_ while [queryHistory] is `false`, the request
     * will be rejected with [ILLEGAL_ARGUMENT][naksha.model.NakshaError.ILLEGAL_ARGUMENT].
     * @since 3.0.0
     */
    // TODO: Change to Int64 aka Long!
    var minVersion: Version? by VERSION_OR_NULL

    /**
     * Limit the read to states at or before the given maximum version, `null` if no limit
     * _(returns the current HEAD state)_.
     *
     * This effectively requests a specific historical snapshot when no [minVersion] is set and
     * [versions] is `1` (the default).
     *
     * If set to anything other than `null` _(the default)_ while [queryHistory] is `false`, the request
     * will be rejected with [ILLEGAL_ARGUMENT][naksha.model.NakshaError.ILLEGAL_ARGUMENT].
     * @since 3.0.0
     */
    // TODO: Change to Int64 aka Long!
    var version: Version? by VERSION_OR_NULL

    /**
     * Order the result-set like given; this is an expensive operation and should be avoided.
     *
     * If an order is required, but no specific one, then it is strongly recommended to stick with the [deterministic order][OrderBy.deterministic], which is produced by creating a blank empty [OrderBy] object or through the static helper method [OrderBy.deterministic]. Ordering by anything else can have a drastic performance impact.
     */
    var orderBy: OrderBy? by ORDER_BY_OR_NULL

    /**
     * Add all features that match the given IDs into the result-set.
     * @since 3.0.0
     */
    @Deprecated("Replaced with op", replaceWith = ReplaceWith("op"))
    var featureIds: StringList by STRING_LIST

    /**
     * Add all features that match the given [GUIDs][naksha.model.Guid] into the result-set.
     *
     * This can be used to load features in specific states.
     * @since 3.0.0
     */
    // TODO: We should replace this with `tupleNumbers`, because that is what we will encode into `uuid` and that is what the clients need.
    //       Is there any use-case for the GUID any longer?
    @Deprecated("Replaced with op", replaceWith = ReplaceWith("op"))
    var guids: GuidList by GUID_LIST

    /**
     * Add all features that match the given query into the result-set.
     * @since 3.0.0
     */
    @Deprecated("Replaced with op", replaceWith = ReplaceWith("op"))
    var query: RequestQuery by QUERY

    /**
     * The [operation][Op] to execute.
     *
     * This replaces [query] and must not be used together with [query]. It actually allows to query for any member value.
     * @since 3.0
     */
    var op: Op? by OP_OR_NULL

    /**
     * Tests whether this request is effectively a query for all features in their current **HEAD** state,
     * i.e. it has no conditions, requests only one state per feature, and does not extend into deleted
     * or historic states.
     *
     * The method ignores an optionally provided [ordering][orderBy].
     * @return `true` if the query is effectively reading all current HEAD states of all features;
     * `false` otherwise.
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
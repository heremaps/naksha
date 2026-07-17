@file:Suppress("OPT_IN_USAGE", "MemberVisibilityCanBePrivate")

package naksha.model.request

import naksha.base.Int64
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.StringList
import naksha.model.GuidList
import naksha.base.Version
import naksha.base.illegalArg
import naksha.model.request.ops.Op
import naksha.model.request.query.IPropertyQuery
import naksha.model.request.query.ITagQuery
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.math.max

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
    @Deprecated("Remove, need always to be done on the client using post-filtering", replaceWith = ReplaceWith("op"))
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
     * Setting this to `true` adds past states from the **HISTORY** section to the result set. When [versions] is greater than `1`, results are ordered automatically by the storage in reverse version order, so the most recent state is returned first.
     */
    var queryHistory: Boolean by BOOLEAN_OR_FALSE

    /**
     * Defines how many states (versions) of each matching feature should be returned _(defaults to `1`)_.
     *
     * - A value of `1` _(the default)_ means only the single latest state closest to the given maximal [version] is returned.
     * - If the underlying JSON map contains a values that is not a number or invalid, the default value `1` will be used.
     *
     * Requesting multiple versions can have a significant performance impact and should be used with care.
     * @since 3.0.0
     */
    var versions: Int
        get() {
            val raw = getRaw("versions")
            if (raw is Int64) return max(1, raw.toInt())
            if (raw is Number) return max(1, raw.toInt())
            return 1
        }
        set(value) {
            if (value < 1) throw illegalArg("versions must not be a value less than 1")
            set("versions", value)
        }

    /**
     * Limit the read to all states at or after the given minimum version, `null` if no limit.
     *
     * If the underlying JSON map contains a values that is not a number or invalid, the default value `null` will be used.
     * @since 3.0.0
     */
    var minVersion: Int64?
        get() {
            val raw = getRaw("minVersion")
            if (raw is Int64) return if (raw < Version.MIN.number || raw > Version.HEAD.number) null else raw
            if (raw is Number) {
                val value = Int64(raw.toLong())
                return if (value < Version.MIN.number || value > Version.HEAD.number) null else value
            }
            return null
        }
        set(value) {
            if (value != null && (value < Version.MIN.number || value > Version.HEAD.number)) {
                throw illegalArg("minVersion must be a value between ${Version.MIN} and ${Version.HEAD}, but was $value")
            }
            set("minVersion", value)
        }

    @JsName("withMinVersionInt64")
    fun withMinVersion(minVersion: Int64?): ReadFeatures {
        this.minVersion = minVersion
        return this
    }

    @JsName("withMinVersion")
    fun withMinVersion(minVersion: Version?): ReadFeatures {
        this.minVersion = minVersion?.number
        return this
    }

    @JsName("withMinVersionLong")
    fun withMinVersion(minVersion: Long?): ReadFeatures {
        this.minVersion = if (minVersion != null) Int64(minVersion) else null
        return this
    }

    /**
     * Limit the read to states at or before the given maximum version, `null` if no limit _([HEAD][Version.VersionCompanion.HEAD])_.
     *
     * This effectively requests a specific historical snapshot, when no [minVersion] is set and [versions] is `1`, which is the default for both parameters.
     *
     * If the underlying JSON map contains a values that is not a number or invalid, the default value `null` will be used.
     * @since 3.0.0
     */
    var version: Int64?
        get() {
            val raw = getRaw("version")
            if (raw is Int64) return raw
            if (raw is Number) return Int64(raw.toLong())
            return null
        }
        set(value) {
            set("version", value)
        }

    @JsName("withVersionInt64")
    fun withVersion(version: Int64?): ReadFeatures {
        this.version = version
        return this
    }

    @JsName("withVersion")
    fun withVersion(version: Version?): ReadFeatures {
        this.version = version?.number
        return this
    }

    @JsName("withVersionLong")
    fun withVersion(version: Long?): ReadFeatures {
        this.version = if (version != null) Int64(version) else null
        return this
    }


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
     * Add all features that match the given [GUIDs][naksha.base.Guid] into the result-set.
     *
     * This can be used to load features in specific states.
     * @since 3.0.0
     */
    // TODO: We should replace this with `tupleNumbers`, because that is what we will encode into `uuid` and that is what the clients need.
    //       Is there any use-case for the GUID any longer?
    @Deprecated("Replace with load by tuple-number, should not be part of the query!", replaceWith = ReplaceWith("op"))
    var guids: GuidList by GUID_LIST

    /**
     * Add all features that match the given query into the result-set.
     * @since 3.0.0
     */
    @Deprecated("Replaced with op", replaceWith = ReplaceWith("op"))
    var query: RequestQuery by QUERY

    /**
     * The [operations][Op] to execute to query members.
     *
     * This replaces [query] and must not be used together with [query]. It actually allows to query for any member value. In doubt, [queryMembers] always wins.
     * @since 3.0
     */
    var queryMembers: Op? by OP_OR_NULL

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
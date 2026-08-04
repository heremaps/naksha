@file:Suppress("OPT_IN_USAGE", "MemberVisibilityCanBePrivate")

package naksha.model.request

import naksha.base.FeatureType.FeatureType_C.CATALOG
import naksha.base.FeatureType.FeatureType_C.COLLECTION
import naksha.base.FeatureType.FeatureType_C.DATABASE
import naksha.base.Id
import naksha.base.IdList
import naksha.base.Int64
import naksha.base.NotNullIdProperty
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.StringList
import naksha.model.GuidList
import naksha.base.Version
import naksha.base.illegalArg
import naksha.base.illegalState
import naksha.model.objects.NakshaCollection
import naksha.model.request.ops.Op
import naksha.model.request.ops.*
import naksha.model.request.query.ITagQuery
import naksha.model.request.query.IPropertyQuery
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.math.max

/**
 * Read features from a [collection][NakshaCollection].
 *
 * If multiple [collections][NakshaCollection] should be queried, then multiple read requests should be issued and the results should be merged, best is to send requests in parallel and then to join the results.
 *
 * @since 3.0.0
 */
@JsExport
open class ReadFeatures() : ReadRequest() {

    /**
     * Initiate a read from the given collection.
     * @param collection the collection from which to read.
     * @since 3.0
     */
    @JsName("forCollection")
    constructor(collection: NakshaCollection) : this() {
        databaseId = collection.databaseId
        catalogId = collection.catalogId
        collectionId = collection.id
    }

    companion object ReadFeatures_C {
        private val ID_NOT_NULL = NotNullIdProperty<ReadRequest>(randomId = false)
        private val ID_LIST = NotNullProperty<ReadRequest, IdList>(IdList::class) { _, _ -> IdList() }
        private val BOOLEAN_OR_FALSE = NotNullProperty<ReadRequest, Boolean>(Boolean::class) { _, _ -> false }
        private val ORDER_BY_OR_NULL = NullableProperty<ReadRequest, OrderBy>(OrderBy::class)
        private val GUID_LIST = NotNullProperty<ReadRequest, GuidList>(GuidList::class)
        private val QUERY = NotNullProperty<ReadRequest, RequestQuery>(RequestQuery::class)
        private val OP_OR_NULL = NullableProperty<ReadRequest, Op>(Op::class)
    }

    /**
     * The `id` of the database from which to read.
     * @since 3.0
     */
    var databaseId: Id by ID_NOT_NULL

    /**
     * Tests if the [databaseId] is set.
     * @since 3.0
     */
    open fun hasDatabaseId(): Boolean = hasIdValue("databaseId")

    /**
     * @see [databaseId]
     */
    open fun withDatabaseId(value: Id): ReadFeatures {
        databaseId = value
        return this
    }

    /**
     * The id of the catalog from which to read.
     *
     * @since 3.0
     */
    var catalogId: Id by ID_NOT_NULL

    /**
     * Tests if the [catalogId] is set.
     * @since 3.0
     */
    open fun hasCatalogId(): Boolean = hasIdValue("catalogId")

    /**
     * @see [catalogId]
     */
    open fun withCatalogId(value: Id): ReadFeatures {
        catalogId = value
        return this
    }


    /**
     * The `id` of the collection to read.
     * @since 3.0
     */
    var collectionId: Id by ID_NOT_NULL

    /**
     * Tests if the [collectionId] is set.
     * @since 3.0
     */
    open fun hasCollectionId(): Boolean = hasIdValue("collectionId")

    /**
     * @see collectionId
     */
    open fun withCollectionId(collectionId: Id): ReadFeatures {
        this.collectionId = collectionId
        return this
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
     * Sets a property query on this read request.
     * @param propertyQuery the property query to set.
     * @return this.
     * @since 3.0
     */
    open fun withPropertyQuery(propertyQuery: IPropertyQuery?): ReadFeatures {
        this.query.properties = propertyQuery
        return this
    }

    /**
     * Extend the request to include features that are in a deleted state _(defaults to `false`)_.
     */
    var queryDeleted: Boolean by BOOLEAN_OR_FALSE

    /**
     * @see queryDeleted
     */
    fun withQueryDeleted(value: Boolean): ReadFeatures {
        this.queryDeleted = value
        return this
    }

    /**
     * Extend the request to search through historic states of features _(defaults to `false`)_.
     *
     * Setting this to `true` adds past states from the **HISTORY** section to the result set. When [versions] is greater than `1`, results are ordered automatically by the storage in reverse version order, so the most recent state is returned first.
     */
    var queryHistory: Boolean by BOOLEAN_OR_FALSE

    /**
     * @see queryHistory
     */
    fun withQueryHistory(value: Boolean): ReadFeatures {
        this.queryHistory = value
        return this
    }

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
    var minVersion: Long?
        get() {
            var raw = getRaw("minVersion")
            if (raw is Int64) raw = raw.toLong()
            if (raw is Long) return if (raw < Version.MIN.number || raw > Version.HEAD.number) null else raw
            if (raw is Number) {
                val value = raw.toLong()
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

    /**
     * @see minVersion
     */
    @JsName("withMinVersionInt64")
    fun withMinVersion(minVersion: Long?): ReadFeatures {
        this.minVersion = minVersion
        return this
    }

    /**
     * @see minVersion
     */
    @JsName("withMinVersion")
    fun withMinVersion(minVersion: Version?): ReadFeatures {
        this.minVersion = minVersion?.number
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
    var version: Long?
        get() {
            val raw = getRaw("version")
            if (raw is Long) return raw
            if (raw is Int64) return raw.toLong()
            if (raw is Number) return raw.toLong()
            return null
        }
        set(value) {
            set("version", value)
        }

    /**
     * @see minVersion
     */
    @JsName("withVersionInt64")
    fun withVersion(version: Long?): ReadFeatures {
        this.version = version
        return this
    }

    /**
     * @see minVersion
     */
    @JsName("withVersion")
    fun withVersion(version: Version?): ReadFeatures {
        this.version = version?.number
        return this
    }

    /**
     * Order the result-set like given; this is an expensive operation and should be avoided.
     *
     * If an order is required, but no specific one, then it is strongly recommended to stick with the [deterministic order][OrderBy.deterministic], which is produced by creating a blank empty [OrderBy] object or through the static helper method [OrderBy.deterministic]. Ordering by anything else can have a drastic performance impact.
     */
    var orderBy: OrderBy? by ORDER_BY_OR_NULL

    /**
     * @see orderBy
     */
    fun withOrderBy(orderBy: OrderBy?): ReadFeatures {
        this.orderBy = orderBy
        return this
    }

    /**
     * Add all features that match the given IDs into the result-set.
     * @since 3.0.0
     */
    @Deprecated("Replaced with op", replaceWith = ReplaceWith("op"))
    var featureIds: IdList by ID_LIST

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
    @Deprecated("Replaced with queryMembers", replaceWith = ReplaceWith("queryMembers"))
    @JsName("queryDeprecated") // We never used this anywhere in JavaScript!
    var query: RequestQuery by QUERY

    /**
     * The [operations][Op] to execute to query members.
     *
     * This replaces [withMemberQuery] and must not be used together with [withMemberQuery]. It actually allows to query for any member value. In doubt, [memberQuery] always wins.
     * @since 3.0
     */
    var memberQuery: Op? by OP_OR_NULL

    /**
     * Add a members query.
     *
     * Available operations are:
     * - [And]
     * - [Equals]
     * - [Gt]
     * - [Gte]
     * - [Intersects]
     * - [IsAnyOf]
     * - [IsFalse]
     * - [IsNull]
     * - [IsTrue]
     * - [Lt]
     * - [Lte]
     * - [Not]
     * - [Or]
     * - [StartsWith]
     * - [TagEquals]
     * - [TagGt]
     * - [TagGte]
     * - [TagIsNull]
     * - [TagListContains]
     * - [TagListContainsAllOf]
     * - [TagListContainsAnyOf]
     * - [TagMapHasAllOf]
     * - [TagMapHasAnyOf]
     * - [TagMapHasKey]
     * - [TagMatches]
     * - [TagStartsWith]
     * @param op the query operation to perform.
     * @since 3.0
     */
    fun withMemberQuery(op: Op): ReadFeatures {
        this.memberQuery = op
        return this
    }

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
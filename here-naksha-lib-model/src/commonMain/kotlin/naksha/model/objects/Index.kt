@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.AnyObject
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.StringList
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * An index on a [NakshaCollection].
 *
 * The [on] list holds the names of the members to index directly. The [include] list holds named of members that should be added into the index, but are not essential part of the indexing. Actually, all indices need to contain the [primary-key][NakshaCollection.indices].
 * @since 3.0
 * @see NakshaCollection.indices
 */
@JsExport
open class Index() : AnyObject() {

    /**
     * Construct a user-defined index.
     * @param name the index name (must be unique within the collection).
     * @param on the member names to index.
     * @since 3.0
     */
    @JsName("IndexByName")
    constructor(name: String, vararg on: String) : this() {
        this.name = name
        val cols = StringList()
        cols.setCapacity(on.size)
        for (c in on) cols.add(c)
        this.on = cols
    }

    /**
     * Construct a user-defined index.
     * @param name the index name (must be unique within the collection).
     * @param on the members to index.
     * @since 3.0
     */
    @JsName("IndexByMember")
    constructor(name: String, vararg on: Member) : this() {
        this.name = name
        val cols = StringList()
        cols.setCapacity(on.size)
        for (c in on) cols.add(c.name)
        this.on = cols
    }

    /**
     * The index name. Must be unique within the collection.
     * @since 3.0
     */
    var name: String by NAME

    /** True iff the underlying map has an entry for [name]. */
    fun hasName(): Boolean = hasRaw("name")

    /** Remove [name] from the underlying map; returns this for chaining. */
    fun withoutName(): Index {
        removeRaw("name")
        return this
    }

    /** Fluent setter for [name]; returns this for chaining. */
    fun withName(value: String): Index {
        name = value
        return this
    }

    /**
     * The names of the members to index. May be names of mandatory/default built-in members
     * (see [StandardMembers]) or names of custom [Member]s declared on the same collection.
     * @since 3.0
     */
    var on: StringList by ON

    /** True iff the underlying map has an entry for [on]. */
    fun hasOn(): Boolean = hasRaw("on")

    /** Remove [on] from the underlying map; returns this for chaining. */
    fun withoutOn(): Index {
        removeRaw("on")
        return this
    }

    /** Fluent setter for [on]; returns this for chaining. */
    fun withOn(value: StringList): Index {
        on = value
        return this
    }

    /**
     * The _(optional)_ sort order of the members within the index.
     *
     * This is just a hint, the index can ignore it. The values must be one of [SortOrder][naksha.model.request.query.SortOrder]. If the list is too short, missing values are appended with order [ANY][naksha.model.request.query.SortOrder.ANY]. Values other than [ASCENDING][naksha.model.request.query.SortOrder.ASCENDING] (`ASC`) and [DESCENDING][naksha.model.request.query.SortOrder.DESCENDING] (`DESC`) are ignored and interpreted as [ANY][naksha.model.request.query.SortOrder.ANY].
     * @since 3.0
     */
    var order: StringList? by STRINGS_NULLABLE

    /** Remove [order] from the underlying map; returns this for chaining. */
    fun withoutOrder(): Index {
        order = null
        return this
    }

    /** Fluent setter for [order]; returns this for chaining. */
    fun withOrder(value: StringList?): Index {
        order = value
        return this
    }

    /**
     * Optional additional columns to cover in the index, allowing index-only lookups. Only used by [IndexType.BTREE].
     * @since 3.0
     */
    var include: StringList? by INCLUDE

    /** True iff the underlying map has an entry for [include]. */
    fun hasInclude(): Boolean = hasRaw("include")

    /** Remove [include] from the underlying map; returns this for chaining. */
    fun withoutInclude(): Index {
        removeRaw("include")
        return this
    }

    /** Fluent setter for [include]; returns this for chaining. */
    fun withInclude(value: StringList?): Index {
        include = value
        return this
    }

    /** Fluent setter for [include]; returns this for chaining. */
    fun withIncludes(vararg names: String): Index {
        val original = this.include
        val include = original ?: StringList()
        for (name in names) {
            if (!include.contains(name)) include.add(name)
        }
        if (original == null) this.include = include
        return this
    }

    /** Fluent setter for [include]; returns this for chaining. */
    @JsName("withIncludesMembers")
    fun withIncludes(vararg members: Member): Index {
        val original = this.include
        val include = original ?: StringList()
        for (member in members) {
            val name = member.name
            if (!include.contains(name)) include.add(name)
        }
        if (original == null) this.include = include
        return this
    }

    /**
     * Whether the index enforces uniqueness across the [on] columns. Defaults to `false`.
     * @since 3.0
     */
    @Deprecated(message = "Removed soon, unique indices are removed, because they do not work well with partitioning")
    private var unique: Boolean by UNIQUE

    /** True iff the underlying map has an entry for [unique]. */
    @Deprecated(message = "Removed soon, unique indices are removed, because they do not work well with partitioning")
    fun isUnique(): Boolean = unique

    /** Fluent setter for [unique]; returns this for chaining. */
    @Deprecated(message = "Removed soon, unique indices are removed, because they do not work well with partitioning")
    internal fun withUnique(value: Boolean): Index {
        unique = value
        return this
    }

    /**
     * Whether the index is conditional: the client declares that only the subset of rows where the leading [on] member has a value needs to be indexed. Defaults to _false_.
     *
     * This is only an intent, not a directive: the storage decides whether and how to honor it, for example by indexing fewer rows. The client never says how the index is built.
     * @since 3.0
     */
    private var conditional: Boolean by CONDITIONAL

    /** True iff this index is [conditional]. */
    fun isConditional(): Boolean = conditional

    /** Remove [conditional] from the underlying map; returns this for chaining. */
    internal fun withoutConditional(): Index {
        removeRaw("conditional")
        return this
    }

    /** Fluent setter for [conditional]; returns this for chaining. */
    internal fun withConditional(value: Boolean): Index {
        conditional = value
        return this
    }

    /**
     * Whether this index is storage-managed (internal). When `true`, the storage fully controls this
     * index — it decides how to create and maintain it — and clients must not attempt to recreate or
     * drop it. Defaults to `false`.
     * @since 3.0
     */
    @Deprecated(message = "Removed soon, only the primary key is internal, we will no longer expose this anyway")
    private var internal: Boolean by INTERNAL

    /** True iff the underlying map has an entry for [internal]. */
    @Deprecated(message = "Removed soon, only the primary key is internal, we will no longer expose this anyway")
    fun isInternal(): Boolean = internal

    /** Fluent setter for [internal]; returns this for chaining. */
    @Deprecated(message = "Removed soon, only the primary key is internal, we will no longer expose this anyway")
    internal fun withInternal(value: Boolean): Index {
        internal = value
        return this
    }

    companion object Index_C {
        private val NAME             = NotNullProperty<Index, String>(String::class) { _, _ -> "" }
        private val ON               = NotNullProperty<Index, StringList>(StringList::class)
        private val STRINGS_NULLABLE = NullableProperty<Index, StringList>(StringList::class, autoRemove = true)
        private val INCLUDE          = NullableProperty<Index, StringList>(StringList::class)
        private val UNIQUE           = NotNullProperty<Index, Boolean>(Boolean::class) { _, _ -> false }
        private val CONDITIONAL      = NotNullProperty<Index, Boolean>(Boolean::class) { _, _ -> false }
        private val INTERNAL         = NotNullProperty<Index, Boolean>(Boolean::class) { _, _ -> false }
    }
}
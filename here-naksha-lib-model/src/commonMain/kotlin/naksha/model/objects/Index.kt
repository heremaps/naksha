@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.PAnyMap
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.StringList
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A user defined index on [Member]'s of a [collection][NakshaCollection].
 *
 * The [on] list holds the names of the members to index. These may be names of mandatory or default built-in members (see [StandardMembers]) or names of custom [Member]s declared on the same collection. What indexing is support is implementation dependent, so depends on the actual storage. For example, `lib-psql` will not support to add a spatial member behind a string member, but vice versa.
 * @since 3.0
 */
@JsExport
open class Index() : PAnyMap() {

    /**
     * Construct a user-defined index.
     * @param name the index name (must be unique within the collection).
     * @param on the member names to index.
     * @since 3.0
     */
    @JsName("of")
    constructor(name: String, vararg on: String) : this() {
        this.name = name
        val cols = StringList()
        cols.setCapacity(on.size)
        for (c in on) cols.add(c)
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
    fun removeName(): Index {
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
    fun removeOn(): Index {
        removeRaw("on")
        return this
    }

    /** Fluent setter for [on]; returns this for chaining. */
    fun withOn(value: StringList): Index {
        on = value
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
    fun removeInclude(): Index {
        removeRaw("include")
        return this
    }

    /** Fluent setter for [include]; returns this for chaining. */
    fun withInclude(value: StringList?): Index {
        include = value
        return this
    }

    /**
     * Whether the index enforces uniqueness across the [on] columns. Defaults to `false`.
     * @since 3.0
     */
    private var unique: Boolean by UNIQUE

    /** True iff the underlying map has an entry for [unique]. */
    fun isUnique(): Boolean = unique

    /** Remove [unique] from the underlying map; returns this for chaining. */
    internal fun removeUnique(): Index {
        removeRaw("unique")
        return this
    }

    /** Fluent setter for [unique]; returns this for chaining. */
    internal fun withUnique(value: Boolean): Index {
        unique = value
        return this
    }

    /**
     * Whether this index is storage-managed (internal). When `true`, the storage controls the DDL
     * for this index (e.g. PRIMARY KEY, UNIQUE with a partial WHERE clause) and clients must not
     * attempt to recreate or drop it. Defaults to `false`.
     * @since 3.0
     */
    private var internal: Boolean by INTERNAL

    /** True iff the underlying map has an entry for [internal]. */
    fun isInternal(): Boolean = internal

    /** Remove [internal] from the underlying map; returns this for chaining. */
    internal fun removeInternal(): Index {
        removeRaw("internal")
        return this
    }

    /** Fluent setter for [internal]; returns this for chaining. */
    internal fun withInternal(value: Boolean): Index {
        internal = value
        return this
    }

    companion object Index_C {
        private val NAME     = NotNullProperty<Index, String>(String::class)
        private val ON       = NotNullProperty<Index, StringList>(StringList::class)
        private val INCLUDE  = NullableProperty<Index, StringList>(StringList::class)
        private val UNIQUE   = NotNullProperty<Index, Boolean>(Boolean::class) { _, _ -> false }
        private val INTERNAL = NotNullProperty<Index, Boolean>(Boolean::class) { _, _ -> false }
    }
}

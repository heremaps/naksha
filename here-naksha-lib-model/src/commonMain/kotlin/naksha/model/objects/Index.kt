@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.AnyObject
import naksha.base.NotNullEnum
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.StringList
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * An index on a [NakshaCollection] — either a mandatory/internal storage-managed index or a
 * user-defined one.
 *
 * The [on] list holds the names of the members to index. These may be names of mandatory or default
 * built-in members (see [StandardMembers]) or names of custom [Member]s declared on the same
 * collection. The [type] decides the storage method:
 * - [IndexType.BTREE]: ordered index for equality and range queries on scalar and text columns.
 * - [IndexType.SPATIAL]: spatial index covering a 2D-encoded geometry column. [on] must contain exactly one geometry member.
 * - [IndexType.TAG_MAP]: inverted index over a tags member ([MemberType.TAG_MAP] or [MemberType.TAG_MAP_FROM_ARRAY]) supporting key/value containment queries. [on] must contain exactly one member.
 * - [IndexType.TAG_LIST]: inverted index over a tag-list member ([MemberType.TAG_LIST]) supporting element containment queries. [on] must contain exactly one member.
 *
 * When [internal] is `true` the index is storage-managed (e.g. primary key, id_unique). The storage
 * injects these into the [NakshaCollection.indices] list; clients must not declare them manually.
 * @since 3.0
 */
@JsExport
open class Index() : AnyObject() {

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
        private val NAME     = NotNullProperty<Index, String>(String::class) { _, _ -> "" }
        private val ON       = NotNullProperty<Index, StringList>(StringList::class)
        private val INCLUDE  = NullableProperty<Index, StringList>(StringList::class)
        private val UNIQUE   = NotNullProperty<Index, Boolean>(Boolean::class) { _, _ -> false }
        private val INTERNAL = NotNullProperty<Index, Boolean>(Boolean::class) { _, _ -> false }
    }
}

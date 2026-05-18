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
 * A user-defined index on a [NakshaCollection].
 *
 * The [on] columns may reference built-in columns (`id`, `geo`, `tags`, ...) or [CustomMember.name]s declared on the same collection. The [type] decides the storage method:
 * - [CustomIndexType.BTREE]: standard B-tree, supports equality and range queries on scalar and `text` columns.
 * - [CustomIndexType.GEO]: GIST + SPGIST pair on a 2D-encoded geometry column. `on` must contain exactly one geometry column.
 * - [CustomIndexType.JSON]: GIN inverted index over a `jsonb` (or built-in `feature` / `tags`) column. `on` must contain exactly one column.
 * @since 3.0
 */
@JsExport
open class CustomIndex() : AnyObject() {

    /**
     * Construct an index.
     * @param name the index name (must be unique within the collection).
     * @param type the index type.
     * @param on the columns to index.
     * @since 3.0
     */
    @JsName("of")
    constructor(name: String, type: CustomIndexType, vararg on: String) : this() {
        this.name = name
        this.type = type
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
    fun removeName(): CustomIndex {
        removeRaw("name")
        return this
    }

    /** Fluent setter for [name]; returns this for chaining. */
    fun withName(value: String): CustomIndex {
        name = value
        return this
    }

    /**
     * The index type.
     * @since 3.0
     */
    var type: CustomIndexType by TYPE

    /** True iff the underlying map has an entry for [type]. */
    fun hasType(): Boolean = hasRaw("type")

    /** Remove [type] from the underlying map; returns this for chaining. */
    fun removeType(): CustomIndex {
        removeRaw("type")
        return this
    }

    /** Fluent setter for [type]; returns this for chaining. */
    fun withType(value: CustomIndexType): CustomIndex {
        type = value
        return this
    }

    /**
     * The columns to index. May reference built-in columns or [CustomMember.name]s on the same collection.
     * @since 3.0
     */
    var on: StringList by ON

    /** True iff the underlying map has an entry for [on]. */
    fun hasOn(): Boolean = hasRaw("on")

    /** Remove [on] from the underlying map; returns this for chaining. */
    fun removeOn(): CustomIndex {
        removeRaw("on")
        return this
    }

    /** Fluent setter for [on]; returns this for chaining. */
    fun withOn(value: StringList): CustomIndex {
        on = value
        return this
    }

    /**
     * Optional columns to include in the index leaf pages (`INCLUDE (...)`). Only used by [CustomIndexType.BTREE].
     * @since 3.0
     */
    var include: StringList? by INCLUDE

    /** True iff the underlying map has an entry for [include]. */
    fun hasInclude(): Boolean = hasRaw("include")

    /** Remove [include] from the underlying map; returns this for chaining. */
    fun removeInclude(): CustomIndex {
        removeRaw("include")
        return this
    }

    /** Fluent setter for [include]; returns this for chaining. */
    fun withInclude(value: StringList?): CustomIndex {
        include = value
        return this
    }

    /**
     * Whether the index enforces uniqueness across the [on] columns. Defaults to `false`.
     * @since 3.0
     */
    var unique: Boolean by UNIQUE

    /** True iff the underlying map has an entry for [unique]. */
    fun hasUnique(): Boolean = hasRaw("unique")

    /** Remove [unique] from the underlying map; returns this for chaining. */
    fun removeUnique(): CustomIndex {
        removeRaw("unique")
        return this
    }

    /** Fluent setter for [unique]; returns this for chaining. */
    fun withUnique(value: Boolean): CustomIndex {
        unique = value
        return this
    }

    companion object CustomIndex_C {
        private val NAME = NotNullProperty<CustomIndex, String>(String::class) { _, _ -> "" }
        private val TYPE = NotNullEnum<CustomIndex, CustomIndexType>(CustomIndexType::class) { _, _ -> CustomIndexType.BTREE }
        private val ON = NotNullProperty<CustomIndex, StringList>(StringList::class)
        private val INCLUDE = NullableProperty<CustomIndex, StringList>(StringList::class)
        private val UNIQUE = NotNullProperty<CustomIndex, Boolean>(Boolean::class) { _, _ -> false }
    }
}

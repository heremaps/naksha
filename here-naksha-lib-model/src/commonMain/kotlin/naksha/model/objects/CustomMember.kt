@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.AnyObject
import naksha.base.NotNullEnum
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A user-defined SQL column materialized on a [NakshaCollection].
 *
 * At write time, the storage walks the feature using [map], extracts the value, coerces it to the [dataType], and stores it in a storage-specific column derived from [name]. The value also remains in the encoded feature blob.
 *
 * The [name] must be a valid Naksha identifier (see [naksha.model.Naksha.verifyId]). Storages typically reserve a separate namespace for user columns (e.g. `lib-psql` materializes a member named `foo` as the physical column `$foo`).
 *
 * If [map] is not set, the storage defaults to `["properties", <name>]` at write time.
 * @since 3.0
 */
@JsExport
open class CustomMember() : AnyObject() {

    /**
     * Construct a member with a name and the given data type.
     * @param name the column name.
     * @param dataType the SQL data type; defaults to [CustomMemberType.STRING].
     * @param map the JSON path to read the value from; defaults to `["properties", name]` when null.
     * @since 3.0
     */
    @JsName("of")
    constructor(name: String, dataType: CustomMemberType = CustomMemberType.STRING, map: JsonPath? = null) : this() {
        this.name = name
        this.dataType = dataType
        if (map != null) this.map = map
    }

    /**
     * The column name. Must match `^[a-z][a-z0-9_]{0,62}$` and must not collide with a reserved built-in column name.
     * @since 3.0
     */
    var name: String by NAME

    /** True iff the underlying map has an entry for [name]. */
    fun hasName(): Boolean = hasRaw("name")

    /** Remove [name] from the underlying map; returns this for chaining. */
    fun removeName(): CustomMember {
        removeRaw("name")
        return this
    }

    /** Fluent setter for [name]; returns this for chaining. */
    fun withName(value: String): CustomMember {
        name = value
        return this
    }

    /**
     * The Postgres data type used to materialize this member as a real SQL column.
     * @since 3.0
     */
    var dataType: CustomMemberType by DATA_TYPE

    /** True iff the underlying map has an entry for [dataType]. */
    fun hasDataType(): Boolean = hasRaw("dataType")

    /** Remove [dataType] from the underlying map; returns this for chaining. */
    fun removeDataType(): CustomMember {
        removeRaw("dataType")
        return this
    }

    /** Fluent setter for [dataType]; returns this for chaining. */
    fun withDataType(value: CustomMemberType): CustomMember {
        dataType = value
        return this
    }

    /**
     * The JSON path to read the value from at write time. If `null`, the storage defaults to `["properties", name]`.
     *
     * Each segment must match `^[A-Za-z_][A-Za-z0-9_]*$`. There is no array indexing in v3.0.
     * @since 3.0
     */
    var map: JsonPath? by MAP

    /** True iff the underlying map has an entry for [map]. */
    fun hasMap(): Boolean = hasRaw("map")

    /** Remove [map] from the underlying map; returns this for chaining. */
    fun removeMap(): CustomMember {
        removeRaw("map")
        return this
    }

    /** Fluent setter for [map]; returns this for chaining. */
    fun withMap(value: JsonPath?): CustomMember {
        map = value
        return this
    }

    /**
     * Returns the effective JSON path to read this member from a feature.
     *
     * If [map] is explicitly set, returns its contents; otherwise returns `["properties", name]`.
     * @since 3.0
     */
    fun effectivePath(): List<String> {
        val m = map
        return if (m != null && m.isNotEmpty()) m.filterNotNull().toList()
        else listOf("properties", name)
    }

    companion object CustomMember_C {
        private val NAME = NotNullProperty<CustomMember, String>(String::class) { _, _ -> "" }
        private val DATA_TYPE = NotNullEnum<CustomMember, CustomMemberType>(CustomMemberType::class) { _, _ -> CustomMemberType.STRING }
        private val MAP = NullableProperty<CustomMember, JsonPath>(JsonPath::class)
    }
}

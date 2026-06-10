@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.AnyObject
import naksha.base.NotNullEnum
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A column materialized on a [NakshaCollection] — either a mandatory/default built-in column or a
 * user-defined one.
 *
 * At write time, the storage walks the feature using [map], extracts the value, coerces it to the
 * [dataType], and stores it in a storage-specific column derived from [name]. The value also remains
 * in the encoded feature blob.
 *
 * The [name] must be a valid Naksha identifier (see [naksha.model.Naksha.verifyId]).
 * Mandatory columns (e.g. `fn`, `version`, `id`, `feature`) are injected by the storage and must
 * not be redeclared by the client with a different type.
 *
 * If [map] is not set, the storage defaults to `["properties", <name>]` at write time.
 * @since 3.0
 */
@JsExport
open class Member() : AnyObject() {

    /**
     * Construct a member with a name and the given data type.
     * @param name the member name.
     * @param dataType the data type; defaults to [MemberType.STRING].
     * @param map the JSON path to read the value from; defaults to `["properties", name]` when null.
     * @since 3.0
     */
    @JsName("of")
    constructor(name: String, dataType: MemberType = MemberType.STRING, map: JsonPath? = null) : this() {
        this.name = name
        this.dataType = dataType
        if (map != null) this.map = map
    }

    /**
     * The column name. Must be a valid Naksha identifier and must not conflict with a reserved built-in
     * column unless it is declared with exactly the same [dataType].
     * @since 3.0
     */
    var name: String by NAME

    /** True iff the underlying map has an entry for [name]. */
    fun hasName(): Boolean = hasRaw("name")

    /** Remove [name] from the underlying map; returns this for chaining. */
    fun removeName(): Member {
        removeRaw("name")
        return this
    }

    /** Fluent setter for [name]; returns this for chaining. */
    fun withName(value: String): Member {
        name = value
        return this
    }

    /**
     * The data type used to materialize this member.
     * @since 3.0
     */
    var dataType: MemberType by DATA_TYPE

    /** True iff the underlying map has an entry for [dataType]. */
    fun hasDataType(): Boolean = hasRaw("dataType")

    /** Remove [dataType] from the underlying map; returns this for chaining. */
    fun removeDataType(): Member {
        removeRaw("dataType")
        return this
    }

    /** Fluent setter for [dataType]; returns this for chaining. */
    fun withDataType(value: MemberType): Member {
        dataType = value
        return this
    }

    /**
     * The JSON path to read the value from at write time. If `null`, the storage defaults to `["properties", name]`.
     *
     * Segments are arbitrary object keys — any valid JSON object key is accepted, including the
     * XYZ namespace identifier `@ns:com:here:xyz`. There is no array indexing in v3.0.
     * @since 3.0
     */
    var map: JsonPath? by MAP

    /** True iff the underlying map has an entry for [map]. */
    fun hasMap(): Boolean = hasRaw("map")

    /** Remove [map] from the underlying map; returns this for chaining. */
    fun removeMap(): Member {
        removeRaw("map")
        return this
    }

    /** Fluent setter for [map]; returns this for chaining. */
    fun withMap(value: JsonPath?): Member {
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

    companion object Member_C {
        private val NAME = NotNullProperty<Member, String>(String::class) { _, _ -> "" }
        private val DATA_TYPE = NotNullEnum<Member, MemberType>(MemberType::class) { _, _ -> MemberType.STRING }
        private val MAP = NullableProperty<Member, JsonPath>(JsonPath::class)
    }
}

@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.AnyObject
import naksha.base.NotNullEnum
import naksha.base.NotNullProperty
import naksha.base.Proxy
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A column materialized on a [NakshaCollection] — either a mandatory/default built-in column or a
 * user-defined one.
 *
 * At write time, the storage walks the feature using [path], extracts the value, coerces it to the
 * [dataType], and stores it in a storage-specific column derived from [name]. The value also remains
 * in the encoded feature blob.
 *
 * The [name] must be a valid Naksha identifier (see [naksha.model.Naksha.verifyId]).
 * Mandatory columns (e.g. `fn`, `version`, `id`, `feature`) are injected by the storage and must
 * not be redeclared by the client with a different type.
 *
 * If [path] is not set, the storage defaults to `["properties", <name>]` at write time.
 * @since 3.0
 */
@JsExport
class Member() : AnyObject() {

    /**
     * Construct a member with a name and the given data type.
     * @param name the member name.
     * @param dataType the data type; defaults to [MemberType.STRING].
     * @param path the JSON path to read the value from; defaults to `["properties", name]` when null.
     * @since 3.0
     */
    @JsName("of")
    constructor(name: String, dataType: MemberType = MemberType.STRING, path: JsonPath? = null) : this() {
        this.name = name
        this.dataType = dataType
        this.path = path ?: JsonPath(listOf("properties", name))
        this.path.validate()
    }

    /**
     * Construct a member copy with a different path, used to relocate standard members to different places.
     *
     * Specifically, this is used to relocate the Tuple-Number and other mandatory members into the deprecated XYZ namespace.
     * @param origin The member to create a copy of.
     * @param path The new path to relocate the member to.
     * @since 3.0
     */
    @JsName("relocate")
    constructor(origin: Member, path: JsonPath) : this() {
        this.name = origin.name
        this.dataType = origin.dataType
        this.path = path
        this.path.validate()
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
     * Each segment must match `^[A-Za-z_][A-Za-z0-9_]*$`. There is no array indexing in v3.0.
     * @since 3.0
     */
    var path: JsonPath by PATH

    /** Fluent setter for [path]; returns this for chaining. */
    fun withPath(value: JsonPath?): Member {
        path = value ?: JsonPath(listOf("properties", name))
        return this
    }

    /**
     * Read this member from the given proxy using the [path] of this member.
     * @param proxy The proxy to read.
     * @return the value of member in that proxy.
     */
    fun read(proxy: Proxy): Any? = proxy.getPath(path)

    /**
     * Whether this member is storage-managed (internal). When `true`, the storage controls the DDL for this member. Defaults to `false`.
     * @since 3.0
     */
    private var internal: Boolean by INTERNAL

    /** True iff the underlying map has an entry for [internal]. */
    fun isInternal(): Boolean = internal

    /** Remove [internal] from the underlying map; returns this for chaining. */
    internal fun removeInternal(): Member {
        removeRaw("internal")
        return this
    }

    /** Fluent setter for [internal]; returns this for chaining. */
    internal fun withInternal(value: Boolean): Member {
        internal = value
        return this
    }

    companion object Member_C {
        private val NAME = NotNullProperty<Member, String>(String::class) { _, _ -> "" }
        private val DATA_TYPE = NotNullEnum<Member, MemberType>(MemberType::class) { _, _ -> MemberType.STRING }
        private val PATH = NotNullProperty<Member, JsonPath>(JsonPath::class) { self, _ -> JsonPath(listOf("properties", self.name)) }
        private val INTERNAL = NotNullProperty<Member, Boolean>(Boolean::class) { _, _ -> false }
    }
}

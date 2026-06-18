@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.AnyObject
import naksha.base.Int64
import naksha.base.MapProxy
import naksha.base.NotNullEnum
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.Proxy
import naksha.geo.SpGeometry
import naksha.model.Naksha
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.NakshaException
import naksha.model.TupleNumber
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A materialized part of a feature.
 *
 * At write time, the storage walks the feature using [path], extracts the value, coerces it to the [dataType], and stores it in a storage-specific location derived from the [name]. The value is removed from the feature, and instead of the actual value a reference into the dedicated storage place is added, so that when decoding the feature, it can be copied back.
 *
 * The [name] must be a valid Naksha identifier (see [naksha.model.Naksha.verifyId]). Mandatory members are injected by the storage and **must not** be redeclared by the client with a different type. Mandatory members are:
 * - [Tuple-Number][naksha.model.objects.StandardMembers.StandardMembers_C.Tn]
 * - [NextVersion][naksha.model.objects.StandardMembers.StandardMembers_C.NextVersion]
 * - [GlobalBookFeatureNumber][naksha.model.objects.StandardMembers.StandardMembers_C.GlobalBookFeatureNumber]
 * - [Id][naksha.model.objects.StandardMembers.StandardMembers_C.Id]
 * - [Feature][naksha.model.objects.StandardMembers.StandardMembers_C.Feature]
 *
 * If [path] is not explicitly set, the implicit path defaults to `["properties", <name>]`.
 * @since 3.0
 */
@JsExport
open class Member() : AnyObject(), Comparator<Member> {

    /**
     * Construct a member with a name and the given data type.
     * @param name the member name.
     * @param dataType the data type; defaults to [MemberType.STRING].
     * @param path the JSON path to read the value from; defaults to `["properties", name]` when null.
     * @since 3.0
     */
    @JsName("of")
    constructor(name: String, dataType: MemberType = MemberType.STRING, path: JsonPath? = null) : this() {
        Naksha.verifyInternalId(name)
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
        if (origin.isVirtual()) throw NakshaException(ILLEGAL_ARGUMENT, "Virtual members can't be copied")
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

    /**
     * The index of this member in the storage; the index is set by the storage ones a collection is created.
     *
     * This value is maintained by the storage; it **MUST NOT** be modified by clients. Storages may manage this value or ignore it, this is implementation dependent. Changes done by the client are ignored by the storage.
     * @since 3.0
     */
    val index: Int? by INDEX

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
     * Whether this member is a storage-managed mandatory member. When `true`, the storage controls the DDL for this member. Defaults to `false`.
     * @since 3.0
     */
    private var mandatory: Boolean by MANDATORY

    /** True if this member is mandatory. */
    fun isMandatory(): Boolean = mandatory

    internal var virtual: Boolean = false
    internal fun withVirtual(): Member {
        this.virtual = true
        return this
    }

    /**
     * True if this member is a virtual member. There are only
     */
    fun isVirtual(): Boolean = mandatory

    /** Remove [mandatory] from the underlying map; returns this for chaining. */
    internal fun removeMandatory(): Member {
        removeRaw("mandatory")
        return this
    }

    /** Fluent setter for [mandatory]; returns this for chaining. */
    internal fun withMandatory(value: Boolean = true): Member {
        mandatory = value
        return this
    }

    /**
     * Ensures that the given `found` member is the same as the `expected` member, allow different path.
     * @param other The member to compare this member with.
     * @param comparePath If the path must be the same as well, defaults to _false_.
     * @return _true_ if the two members are the same; _false_ otherwise.
     */
    fun isSameAs(other: Member?, comparePath: Boolean = false): Boolean {
        if (other == null) return false
        if (this === other) return true
        // We require same name and data-type, but not same JSON path.
        if (name != other.name) return false
        if (dataType != other.dataType) return false
        if (comparePath && !path.contentDeepEquals(other.path)) return false
        return true
    }

    /**
     * Ensures that the given `other` member is the same as this.
     * @param other The member to compare this with.
     * @param comparePath If the path must be the same as well, defaults to _false_.
     * @return The `other` member, if it is the same as this.
     * @throws NakshaException with error [ILLEGAL_STATE], when the given `other` members does not match this member.
     */
    fun asSame(other: Member?, comparePath: Boolean = false): Member {
        if (other == null) throw NakshaException(ILLEGAL_STATE, "The other member is NULL")
        if (other === this) return other
        // We require same name and data-type, but not same JSON path.
        if (name != other.name) {
            throw NakshaException(ILLEGAL_STATE, "The other member has different name: '${other.name}', expected: '${name}'")
        }
        if (dataType != other.dataType) {
            throw NakshaException(ILLEGAL_STATE, "The other member has wrong data type: '${other.dataType}', expected '${dataType}'")
        }
        if (comparePath && !path.contentDeepEquals(other.path)) {
            throw NakshaException(ILLEGAL_STATE, "The other member has a different path: '${other.path.joinToString("->")}', expected: '${path.joinToString("->")}'")
        }
        return other
    }

    /**
     * Helper to read a [TupleNumber] form the given feature.
     * @param feature The feature to read from.
     * @return the read value or `null`, if the feature does not store a valid value at the member path.
     */
    fun getTupleNumber(feature: MapProxy<*,*>): TupleNumber? {
        val raw = feature.getPath(path)
        if (raw is TupleNumber) return raw
        if (raw is String) return TupleNumber.fromString(raw)
        return null
    }

    /**
     * Helper to read a string form the given feature.
     * @param feature The feature to read from.
     * @return the read value or `null`, if the feature does not store a valid value at the member path.
     */
    fun getBoolean(feature: MapProxy<*,*>): Boolean? {
        val raw = feature.getPath(path)
        if (raw is Boolean) return raw
        return null
    }

    /**
     * Helper to read a string form the given feature.
     * @param feature The feature to read from.
     * @return the read value or `null`, if the feature does not store a valid value at the member path.
     */
    fun getString(feature: MapProxy<*,*>): String? {
        val raw = feature.getPath(path)
        if (raw is String) return raw
        return null
    }

    /**
     * Helper to read a 64-bit integer form the given feature.
     * @param feature The feature to read from.
     * @return the read value or `null`, if the feature does not store a valid value at the member path.
     */
    fun getInt64(feature: MapProxy<*,*>): Int64? {
        val raw = feature.getPath(path)
        if (raw is Int64) return raw
        if (raw is Long) return Int64(raw)
        if (raw is Number) return Int64(raw.toLong())
        return null
    }

    /**
     * Helper to read a 64-bit floating point number form the given feature.
     * @param feature The feature to read from.
     * @return the read value or `null`, if the feature does not store a valid value at the member path.
     */
    fun getDouble(feature: MapProxy<*,*>): Double? {
        val raw = feature.getPath(path)
        if (raw is Double) return raw
        if (raw is Number) return raw.toDouble()
        return null
    }

    /**
     * Helper to read a 64-bit floating point number form the given feature.
     * @param feature The feature to read from.
     * @return the read value or `null`, if the feature does not store a valid value at the member path.
     */
    fun getGeometry(feature: MapProxy<*,*>): SpGeometry? {
        val raw = feature.getPath(path)
        if (raw is SpGeometry) return raw
        return null
    }

    /**
     * Helper to read a 64-bit floating point number form the given feature.
     * @param feature The feature to read from.
     * @return the read value or `null`, if the feature does not store a valid value at the member path.
     */
    fun getByteArray(feature: MapProxy<*,*>): ByteArray? {
        val raw = feature.getPath(path)
        if (raw is ByteArray) return raw
        return null
    }

    /**
     * Helper to write a member value to the given feature.
     * @param feature The feature to write to.
     * @return the previous value.
     * @throws RuntimeException If the given feature has a broken path, so the path requires an array, but an object exists already.
     */
    fun set(feature: MapProxy<*,*>, value: Any?): Any? = feature.setPath(value, path)

    override fun compare(a: Member, b: Member): Int = a.dataType.sortOrder - b.dataType.sortOrder

    companion object Member_C {
        private val NAME = NotNullProperty<Member, String>(String::class) { _, _ -> "" }
        private val DATA_TYPE = NotNullEnum<Member, MemberType>(MemberType::class) { _, _ -> MemberType.STRING }
        private val INDEX = NullableProperty<Member, Int>(Int::class)
        private val PATH = NotNullProperty<Member, JsonPath>(JsonPath::class) { self, _ -> JsonPath(listOf("properties", self.name)) }
        private val MANDATORY = NotNullProperty<Member, Boolean>(Boolean::class) { _, _ -> false }
    }
}

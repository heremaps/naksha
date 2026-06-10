@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.base.WeakRef
import naksha.jbon.IBook
import naksha.model.objects.Member
import naksha.geo.SpGeometry
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StandardMembers
import kotlin.js.JsExport
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

/**
 * A tuple represents a specific immutable state of a feature.
 *
 * The tuple stores the address fields ([storageNumber], [mapNumber], [collectionNumber], [featureNumber], [version]) as primary constructor parameters, with an optional [members] dict for values stored in dedicated member slots of the storage.
 *
 * @since 3.0
 */
@JsExport
data class Tuple(
    /**
     * The storage-number (database-level identifier).
     * @since 3.0
     */
    @JvmField val storageNumber: Int64,

    /**
     * The map-number (catalog-level identifier).
     * @since 3.0
     */
    @JvmField val mapNumber: Int,

    /**
     * The collection-number.
     * @since 3.0
     */
    @JvmField val collectionNumber: Int,

    /**
     * The feature-number.
     * @since 3.0
     */
    @JvmField val featureNumber: Int64,

    /**
     * The version (transaction-number with action bits).
     * @since 3.0
     */
    @JvmField val version: Version,

    /**
     * The next-version at which this tuple was superseded. `NULL`-sentinel (`-1L`) indicates the tuple is the current (HEAD) state.
     * @since 3.0
     */
    @JvmField var nextVersion: Int64 = Int64(-1L),

    /**
     * Optional members dict provided by storage at read time. Contains metadata values such as `id`, `app_id`, `updated_at`, `data_encoding`, etc.
     * @since 3.0
     */
    @JvmField val members: IBook? = null,

    /**
     * Feature serialized with the encoding described by the collection's dataEncoding.
     * @since 3.0
     */
    @JvmField val feature: ByteArray? = null,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is Tuple && this.tupleNumber == other.tupleNumber
    }

    override fun hashCode(): Int = tupleNumber.hashCode()

    private var _weakRef: WeakRef<Tuple>? = null

    /**
     * A lazy created weak-reference to this tuple _(created on read)_.
     * @since 3.0
     */
    val weakRef: WeakRef<Tuple>
        get() {
            var ref = _weakRef
            if (ref == null) {
                ref = WeakRef(this)
                _weakRef = ref
            }
            return ref
        }

    private var _tupleNumber: TupleNumber? = null

    /**
     * The [TupleNumber] of the [Tuple], lazily cached.
     * @since 3.0
     */
    val tupleNumber: TupleNumber
        get() {
            var tn = _tupleNumber
            if (tn == null) {
                tn = TupleNumber(storageNumber, mapNumber, collectionNumber, featureNumber, version)
                _tupleNumber = tn
            }
            return tn
        }

    private var _nextTupleNumber: TupleNumber? = null

    /**
     * The [TupleNumber] of the next version, if [nextVersion] is set (not `-1L`).
     * Lazily cached.
     * @since 3.0
     */
    val nextTupleNumber: TupleNumber?
        get() {
            if (nextVersion == Int64(-1L)) return null
            var tn = _nextTupleNumber
            if (tn == null) {
                tn = TupleNumber(storageNumber, mapNumber, collectionNumber, featureNumber, Version(nextVersion))
                _nextTupleNumber = tn
            }
            return tn
        }

    /**
     * The feature as deserialized [NakshaFeature]. Lazily cached.
     * @since 3.0
     */
    val nakshaFeature: NakshaFeature?
        get() = _nakshaFeature
    private var _nakshaFeature: NakshaFeature? = null

    private var _id: String? = null

    /**
     * The feature identifier.
     * If [featureNumber] is non-negative, returns the stringified feature-number.
     * If [featureNumber] is negative, reads the custom identifier from [members].
     * Lazily cached.
     * @since 3.0
     */
    val id: String
        get() {
            var result = _id
            if (result == null) {
                result = if (featureNumber >= 0) featureNumber.toString() else getStringMember(StandardMembers.Id)
                    ?: throw IllegalStateException("Missing 'id' member for tuple with negative feature-number: $tupleNumber")
                _id = result
            }
            return result
        }

    /**
     * Get a String member by name.
     * Returns `null` if the member is missing, the value is `null`, or not a String.
     * @since 3.0
     */
    fun getStringMember(member: Member): String? =
        members?.getByName(member.name)?.let { v -> if (v is String) v else null }

    /**
     * Get a long member by name with a default.
     * Returns [alt] if the member is missing or not a long-compatible type.
     * @since 3.0
     */
    @JvmOverloads
    fun getLongMember(member: Member, alt: Int64 = Int64(0L)): Int64 =
        members?.getByName(member.name)?.let { v ->
            when (v) {
                is Int64 -> v
                is Long -> Int64(v)
                is Number -> Int64(v.toLong())
                else -> alt
            }
        } ?: alt

    /**
     * Get an int member by name with a default.
     * Returns [alt] if the member is missing or not an int-compatible type.
     * @since 3.0
     */
    @JvmOverloads
    fun getIntMember(member: Member, alt: Int = 0): Int =
        members?.getByName(member.name)?.let { v ->
            when (v) {
                is Int -> v
                is Number -> v.toInt()
                else -> alt
            }
        } ?: alt

    /**
     * Get a double member by name with a default.
     * Returns [alt] if the member is missing or not a double-compatible type.
     * @since 3.0
     */
    @JvmOverloads
    fun getDoubleMember(member: Member, alt: Double = Double.NaN): Double =
        members?.getByName(member.name)?.let { v ->
            when (v) {
                is Double -> v
                is Number -> v.toDouble()
                else -> alt
            }
        } ?: alt

    /**
     * Get a boolean member by name with a default.
     * Returns [alt] if the member is missing or not a boolean.
     * @since 3.0
     */
    @JvmOverloads
    fun getBooleanMember(member: Member, alt: Boolean = false): Boolean =
        members?.getByName(member.name)?.let { v -> if (v is Boolean) v else alt } ?: alt

       /**
     * Get the raw value of a member by name.
     * Returns `null` if the member is missing or the value is `null`.
     * @since 3.0
     */
    fun getMember(member: Member): Any? = members?.getByName(member.name)

    /**
     * Get a ByteArray member by name.
     * Returns `null` if the member is missing, the value is `null`, or not a ByteArray.
     * @since 3.0
     */
    fun getByteArray(member: Member): ByteArray? =
        members?.getByName(member.name)?.let { v -> if (v is ByteArray) v else null }

    /**
     * Get a geometry member by name.
     * Decodes the TWKB bytes into [SpGeometry].
     * Returns `null` if the member is missing, the value is `null`, or not a ByteArray.
     * @since 3.0
     */
    fun getSpatialMember(member: Member): SpGeometry? {
        val bytes = members?.getByName(member.name) as? ByteArray ?: return null
        return try { Naksha.decodeGeometry(bytes) } catch (_: Exception) { null }
    }

    /**
     * Get a tags member by name.
     * Decodes the JSON text into a [TagMap].
     * Returns `null` if the member is missing, the value is `null`, or not a String.
     * @since 3.0
     */
    fun getTags(member: Member): TagMap? {
        val json = members?.getByName(member.name) as? String ?: return null
        return try { Naksha.decodeTags(json) } catch (_: Exception) { null }
    }

    /**
     * Get a tags member by name as a [TagList].
     * Decodes the JSON text, supporting both persisted forms: a JSON array
     * ([set][naksha.model.objects.MemberType.SET], the default — order preserved) and a JSON object
     * ([naksha.model.objects.MemberType.TAGS_FROM_ARRAY] — re-flattened, order not guaranteed).
     * Returns `null` if the member is missing, the value is `null`, or not a String.
     * @since 3.0
     */
    fun getTagList(member: Member): TagList? {
        val json = members?.getByName(member.name) as? String ?: return null
        return try { Naksha.decodeTagList(json) } catch (_: Exception) { null }
    }

    /**
     * The [DataEncoding] of this tuple, read from [members].
     * Returns [Naksha.DEFAULT_DATA_ENCODING] if not set.
     * @since 3.0
     */
    val dataEncoding: DataEncoding
        get() {
            val str = members?.getByName("data_encoding") as? String ?: return Naksha.DEFAULT_DATA_ENCODING
            return try { DataEncoding.fromString(str) } catch (_: Exception) { Naksha.DEFAULT_DATA_ENCODING }
        }

    /**
     * Convert this [Tuple] into a [NakshaFeature] by decoding the feature blob and building the XYZ namespace.
     * @return the decoded [NakshaFeature], or `null` if the tuple has no feature data.
     * @since 3.0
     */
    fun toNakshaFeature(): NakshaFeature? {
        val feature = Naksha.decodeFeature(this.feature, dataEncoding, null) ?: return null
        feature.properties.xyz = XyzNs.fromTuple(this)
        val tags = getTagList(StandardMembers.Tags)
        if (tags != null) {
            feature.properties.xyz.tags = tags
        }
        val geoBytes = getByteArray(StandardMembers.Geometry)
        if (geoBytes != null) {
            feature.geometry = Naksha.decodeGeometry(geoBytes)
        }
        return feature
    }
}

@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.base.WeakRef
import naksha.model.objects.NakshaFeature
import kotlin.js.JsExport
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

/**
 * A tuple represents a specific immutable state of a feature on the heap. The default constructor creates a metadata-only entry.
 * @since 3.0
 */
@JsExport
data class Tuple(
    /**
     * The metadata, this is going into the [XYZ namespace][XyzNs], when decoding the [Tuple] into a [NakshaFeature].
     * @since 3.0
     */
    @JvmField val meta: Metadata,

    /**
     * Feature encoded with [FeatureEncoding] algorithm described by [Metadata.flags].
     * @since 3.0
     */
    @JvmField val feature: ByteArray? = null,

    /**
     * Geometry encoded as raw `TWKB`.
     *
     * Might be _null_, when the feature does not have a geometry.
     * @since 3.0
     */
    @JvmField val geo: ByteArray? = null,

    /**
     * Geometry-Reference-Point, always a single [point][naksha.geo.SpPoint], [TWKB](https://github.com/TWKB/Specification) encoded (no compression, we never get any advantage of compression).
     *
     * Might be _null_, when the feature does not have a reference point, in that the [geo-grid HERE tile-id][Metadata.calculateHereTile] is calculated from the gravitational center of the [geometry][geo], or, if the feature does not have a geometry either, then it is calculated from the [id][Metadata.id] of the feature.
     * @since 3.0
     */
    @JvmField val referencePoint: ByteArray? = null,

    /**
     * Tags encoded as `JBON_GZIP`.
     *
     * Might be _null_, when the feature does not have any tags.
     * @since 3.0
     */
    @JvmField val tags: ByteArray? = null,

    /**
     * An arbitrary binary attachment.
     * @since 3.0
     */
    @JvmField val attachment: ByteArray? = null,

    /**
     * If the [Tuple] is complete, otherwise this is a partial tuple, which can't be cached.
     * @since 3.0
     */
    @JvmField val complete: Boolean = false,
) : ITuple {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is Tuple && this.meta == other.meta
    }

    override fun hashCode(): Int = super.hashCode()

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

    /**
     * The [identifier][Metadata.id] of the feature, basically `meta.id`.
     * @since 3.0
     */
    val id: String
        get() = meta.id

    /**
     * The [tuple-number][TupleNumber] of the [Tuple].
     * @since 3.0
     */
    val tupleNumber: TupleNumber
        get() = meta.tupleNumber

    /**
     * The number of the storage in which the tuple is stored.
     * @since 3.0
     */
    val storageNumber: Int64
        get() = meta.storageNumber

    /**
     * The number of the map in which the tuple is stored.
     * @since 3.0
     */
    val mapNumber: Int
        get() = meta.mapNumber

    /**
     * The number of the collection in which the tuple is stored.
     * @since 3.0
     */
    val collectionNumber: Int
        get() = meta.collectionNumber

    /**
     * The number of the feature.
     * @since 3.0
     */
    val featureNumber: Int64
        get() = meta.featureNumber

    /**
     * The partition-number in the tuple is stored.
     * @since 3.0
     */
    val partitionNumber: Int
        get() = meta.partitionNumber

    /**
     * The version of the [Tuple].
     * @since 3.0
     */
    val version: Version
        get() = meta.version

    /**
     * Convert the tuple into a [Naksha feature][NakshaFeature], using the [Naksha.cache] to query for the [dictionary-manager][naksha.jbon.IDictManager].
     *
     * There is no caching involved, every call of this method will perform another conversion.
     * @return this tuple as Naksha feature.
     * @see [Naksha.decodeTuple]
     */
    fun toNakshaFeature(): NakshaFeature = Naksha.decodeTuple(this)

    private var guid: Guid? = null

    /**
     * Return the [Guid] for this tuple, requires [complete], otherwise throws a [NakshaError.ILLEGAL_STATE].
     * - Throws [NakshaError.ILLEGAL_STATE], if the [Tuple] is not [complete].
     * @return the [Guid] of this tuple.
     */
    fun toGuid(): Guid {
        var g = guid
        if (g == null) {
            g = Guid(meta.id, meta.tupleNumber)
            guid = g
        }
        return g
    }

    /**
     * Tests if this [Tuple] is loaded completely.
     * @return _true_ if this [Tuple] is loaded completely; _false_ otherwise.
     */
    fun isComplete(): Boolean = complete

    override fun toTuple(): Tuple = this
}


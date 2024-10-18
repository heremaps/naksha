@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.objects.NakshaFeature
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * A tuple represents a specific immutable state of a feature.
 * @since 3.0.0
 */
@JsExport
data class Tuple(
    /**
     * The metadata, this is going into the [XYZ namespace][XyzNs], when decoding the [Tuple] into a [NakshaFeature].
     * @since 3.0.0
     */
    @JvmField val meta: Metadata,

    /**
     * Feature encoded with [FeatureEncoding] algorithm described by [Metadata.flags].
     * @since 3.0.0
     */
    @JvmField val feature: ByteArray? = null,

    /**
     * Geometry encoded with [GeoEncoding] algorithm described by [Metadata.flags].
     * Might be _null_, when the feature does not have a geometry.
     * @since 3.0.0
     */
    @JvmField val geo: ByteArray? = null,

    /**
     * Geometry-Reference-Point, encoded with the [GeoEncoding] algorithm described by [Metadata.flags].
     * Might be _null_, when the feature does not have a reference point.
     * @since 3.0.0
     */
    @JvmField val referencePoint: ByteArray? = null,

    /**
     * Tags encoded with [TagsEncoding] algorithm described by [Metadata.flags].
     * Might be _null_, when the feature does not have any tags.
     * @since 3.0.0
     */
    @JvmField val tags: ByteArray? = null,

    /**
     * An arbitrary binary attachment.
     * @since 3.0.0
     */
    @JvmField val attachment: ByteArray? = null,

    /**
     * The bits about which parts of the tuple have been fetched.
     *
     * If the client wants to create a tuple for internal purpose or to write a new state into a storage, then it should use [FETCH_ALL].
     * @since 3.0.0
     */
    @JvmField val state: FetchMode,
) : ITuple {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is Tuple && this.meta == other.meta
    }

    override fun hashCode(): Int = super.hashCode()

    /**
     * Return the number of the storage in which the tuple is stored.
     * @since 3.0.0
     */
    val storageNumber: Int64
        get() = meta.storageNumber

    /**
     * Return the number of the map in which the tuple is stored.
     * @since 3.0.0
     */
    val mapNumber: Int
        get() = meta.storeNumber.mapNumber()

    /**
     * Return the number of the collection in which the tuple is stored.
     * @since 3.0.0
     */
    val collectionNumber: Int
        get() = meta.storeNumber.collectionNumber()

    /**
     * Return the partition-number in the tuple is stored.
     * @since 3.0.0
     */
    val partitionNumber: Int
        get() = meta.storeNumber.partitionNumber()

    /**
     * Convert the tuple into a [Naksha feature][NakshaFeature], using the [NakshaCache] to query for the [tuple-codec][ITupleCodec].
     *
     * There is no caching involved, every call of this method will perform another convertion.
     *
     * - Throws [NakshaError.ILLEGAL_STATE], if the storage-number of the tuple can't be resolved into a [codec][ITupleCodec].
     * @return this tuple as Naksha feature.
     */
    fun toNakshaFeature(): NakshaFeature {
        val tupleCodec = NakshaCache.getTupleCodec(meta.storageNumber)
            ?: throw NakshaException(ILLEGAL_STATE, "Failed to find tuple-codec in NakshaCache for storage-number ${meta.storageNumber}")
        return tupleCodec.tupleToFeature(this)
    }

    private var guid: Guid? = null

    /**
     * Return the [Guid] for this tuple, requires that [meta] is not _null_, otherwise throws a [NakshaError.ILLEGAL_STATE].
     * @return the [Guid] of this tuple.
     */
    fun toGuid(): Guid {
        var g = guid
        if (g == null) {
            g = Guid(meta.id, meta.tupleNumber())
            guid = g
        }
        return g
    }

    /**
     * Merge the given [Tuple] with this one.
     *
     * As tuples are immutable, the order should not be significant, actually, the [state] is checked to understand which properties are valid.
     *
     * This is basically done, when more details become available about a tuple in the cache.
     *
     * - Throws [NakshaError.ILLEGAL_ARGUMENT], if the given other tuple is not referring to the same tuple.
     * @param other the tuple to merge this with (must be the same tuple).
     * @return either this, [other], or a new tuple that merge the two.
     */
    fun merge(other: Tuple?): Tuple {
        if (other == null) return this
        if (meta != other.meta) throw NakshaException(ILLEGAL_ARGUMENT, "Can't merge two different tuples")
        val meta = this.meta
        val nextVersion = meta.nextVersion ?: other.meta.nextVersion
        if (state.isComplete() && meta.nextVersion == nextVersion) {
            return this
        }
        if (other.state.isComplete() && other.meta.nextVersion == nextVersion) {
            return other
        }
        val newMeta = if (meta.nextVersion == nextVersion) {
            meta
        } else if (other.meta.nextVersion == nextVersion) {
            other.meta
        } else {
            meta.copy(nextVersion = meta.nextVersion)
        }
        return Tuple(
            newMeta,
            feature ?: other.feature,
            geo ?: other.geo,
            referencePoint ?: other.referencePoint,
            tags ?: other.tags,
            attachment ?: other.attachment,
            state or other.state,
        )
    }

    /**
     * Tests if the tuple is fetched completely.
     * @return _true_, when the tuple is fully fetched; _false_ if parts are missing.
     */
    fun isComplete(): Boolean = state.isComplete()

    override fun toTuple(): Tuple = this
}
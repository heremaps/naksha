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
     * The tuple-number, a unique identifier for the tuple.
     *
     * If the client wants to create a tuple for internal purpose or to write a new state into a storage, then it should use [TupleNumber.UNDEFINED].
     * @since 3.0.0
     */
    @JvmField val tupleNumber: TupleNumber,

    /**
     * The bits about which parts of the tuple have been fetched.
     *
     * If the client wants to create a tuple for internal purpose or to write a new state into a storage, then it should use [FetchMode.FETCH_ALL].
     * @since 3.0.0
     */
    @JvmField val fetchBits: FetchBits,

    /**
     * The metadata, this is going into the [XYZ namespace][XyzNs], when decoding the [Tuple] into a [NakshaFeature].
     * @since 3.0.0
     */
    @JvmField val meta: Metadata? = null,

    /**
     * The `id` of the feature.
     * @since 3.0.0
     */
    @JvmField val id: String = meta?.id ?: throw NakshaException(ILLEGAL_ARGUMENT, "Either meta or id must be provided"),

    /**
     * The `flags` of the feature.
     * @since 3.0.0
     */
    @JvmField val flags: Flags = meta?.flags ?: throw NakshaException(ILLEGAL_ARGUMENT, "Either meta or flags must be provided"),

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
    @JvmField val attachment: ByteArray? = null
) : ITuple {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is Tuple && this.tupleNumber == other.tupleNumber
    }

    override fun hashCode(): Int = super.hashCode()

    /**
     * Return the number of the storage in which the tuple is stored.
     *
     * This is an alias for `tupleNumber.storageNumber`.
     * @since 3.0.0
     */
    val storageNumber: Int64
        get() = tupleNumber.storageNumber

    /**
     * Return the number of the map in which the tuple is stored.
     *
     * This is an alias for `tupleNumber.mapNumber()`.
     * @since 3.0.0
     */
    val mapNumber: Int
        get() = tupleNumber.mapNumber()

    /**
     * Return the number of the collection in which the tuple is stored.
     *
     * This is an alias for `tupleNumber.collectionNumber()`.
     * @since 3.0.0
     */
    val collectionNumber: Int
        get() = tupleNumber.collectionNumber()

    /**
     * Return the partition-number in the tuple is stored.
     *
     * This is an alias for `tupleNumber.partitionNumber()`.
     * @since 3.0.0
     */
    val partitionNumber: Int
        get() = tupleNumber.partitionNumber()

    /**
     * Convert the tuple into a [Naksha feature][NakshaFeature], using the [NakshaCache] to query for the [tuple-codec][ITupleCodec].
     *
     * There is no caching involved, every call of this method will perform another convertion.
     *
     * - Throws [NakshaError.ILLEGAL_STATE], if the storage-number of the tuple can't be resolved into a [codec][ITupleCodec].
     * @return this tuple as Naksha feature.
     */
    fun toNakshaFeature(): NakshaFeature {
        val tupleCodec = NakshaCache.getTupleCodec(tupleNumber.storageNumber)
            ?: throw NakshaException(ILLEGAL_STATE, "Failed to find tuple-codec in NakshaCache for storage-number ${tupleNumber.storageNumber}")
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
            g = Guid(id, tupleNumber)
            guid = g
        }
        return g
    }

    /**
     * Merge the same tuples into a new one, if this tuple is up-to-date, the method returns this tuple.
     *
     * This is basically done, when more details become available about a tuple in the cache.
     *
     * - Throws [NakshaError.ILLEGAL_ARGUMENT], if the given other tuple is not referring to the same tuple.
     * @param other the tuple to merge this with (must be the same tuple, just with different information details).
     * @return a new tuple, where nothing is _null_.
     */
    fun merge(other: Tuple): Tuple {
        if (tupleNumber != other.tupleNumber || id != other.id) {
            throw NakshaException(ILLEGAL_ARGUMENT, "Can't merge two different tuples")
        }
        val meta = this.meta
        val otherNextVersion = other.meta?.nextVersion
        if (otherNextVersion != null && meta != null && meta.nextVersion == null) meta.nextVersion = otherNextVersion
        if (fetchBits.isComplete() || fetchBits == other.fetchBits) {
            return this
        }
        if (other.fetchBits.isComplete()) return other.merge(this)

        // There must be a difference, we need to create a new merged tuple.
        return Tuple(
             tupleNumber,
            fetchBits or other.fetchBits,
            meta ?: other.meta,
            id,
            flags,
            feature ?: other.feature,
            geo ?: other.geo,
            referencePoint ?: other.referencePoint,
            tags ?: other.tags,
            attachment ?: other.attachment
        )
    }

    /**
     * Tests if the tuple is fetched completely.
     * @return _true_, when the tuple is fully fetched; _false_ if parts are missing.
     */
    fun isComplete(): Boolean = fetchBits.isComplete()

    override fun toTuple(): Tuple = this
}
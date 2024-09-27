@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.model.NakshaError.NakshaErrorCompanion.COLLECTION_NOT_FOUND
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.NakshaError.NakshaErrorCompanion.MAP_NOT_FOUND
import naksha.model.objects.NakshaFeature
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A tuple represents a specific immutable state of a feature in a storage.
 */
@JsExport
data class Tuple(
    /**
     * Reference to specific storage implementation that allows to decode tuple to feature.
     */
    @JvmField val storage: IStorage,

    /**
     * The tuple-number, a unique identifier for the tuple.
     */
    @JvmField val tupleNumber: TupleNumber,

    /**
     * The bits about which parts of the tuple have been fetched.
     */
    @JvmField val fetchBits: FetchBits,

    /**
     * The metadata, this is going into the [XYZ namespace][XyzNs], when decoding the [Tuple] into a [NakshaFeature].
     */
    @JvmField val meta: Metadata? = null,

    /**
     * The `id` of the feature.
     */
    @JvmField val id: String = meta?.id ?: throw NakshaException(ILLEGAL_ARGUMENT, "Either meta or id must be provided"),

    /**
     * The `flags` of the feature.
     */
    @JvmField val flags: Flags = meta?.flags ?: throw NakshaException(ILLEGAL_ARGUMENT, "Either meta or flags must be provided"),

    /**
     * Feature encoded with [FeatureEncoding] algorithm described by [Metadata.flags].
     */
    @JvmField val feature: ByteArray? = null,

    /**
     * Geometry encoded with [GeoEncoding] algorithm described by [Metadata.flags].
     * Might be _null_, when the feature does not have a geometry.
     */
    @JvmField val geo: ByteArray? = null,

    /**
     * Geometry-Reference-Point, encoded with the [GeoEncoding] algorithm described by [Metadata.flags].
     * Might be _null_, when the feature does not have a reference point.
     */
    @JvmField val referencePoint: ByteArray? = null,

    /**
     * Tags encoded with [TagsEncoding] algorithm described by [Metadata.flags].
     * Might be _null_, when the feature does not have any tags.
     */
    @JvmField val tags: ByteArray? = null,

    /**
     * An arbitrary binary attachment.
     */
    @JvmField val attachment: ByteArray? = null
) : ITuple {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is Tuple && this.tupleNumber == other.tupleNumber
    }

    override fun hashCode(): Int = super.hashCode()

    val mapNumber: Int
        get() = tupleNumber.mapNumber()
    val mapId: String?
        get() = storage.getMapId(mapNumber)
    val collectionNumber: Int64
        get() = tupleNumber.collectionNumber()
    val collectionId: String?
        get() {
            val mapId = this.mapId ?: return null
            val map = storage[mapId]
            if (!map.exists()) return null
            return map.getCollectionId(collectionNumber)
        }

    /**
     * Maps tuple into a Naksha feature.
     * @return this tuple as Naksha feature.
     */
    fun toNakshaFeature(): NakshaFeature = storage.tupleToFeature(this)

    private var guid: Guid? = null

    /**
     * Return the [Guid] for this tuple, requires that [meta] is not _null_, otherwise throws a [NakshaError.ILLEGAL_STATE].
     * @return the [Guid] of this tuple.
     */
    fun toGuid(): Guid {
        var g = guid
        if (g == null) {
            val meta = this.meta ?: throw NakshaException(ILLEGAL_STATE, "Without metadata it is not possible to generate the GUID from a tuple")
            val mapNumber = meta.storeNumber.mapNumber()
            val mapId = storage.getMapId(mapNumber) ?: throw NakshaException(MAP_NOT_FOUND, "Map #$mapNumber not found")
            val map = storage[mapId]
            val collectionNumber = meta.storeNumber.collectionNumber()
            val collectionId = map.getCollectionId(collectionNumber) ?: throw NakshaException(
                COLLECTION_NOT_FOUND,
                "Collection #$collectionNumber not found"
            )
            g = Guid(storage.id, mapId, collectionId, meta.id, meta.version, meta.uid)
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
        if (storage != other.storage || tupleNumber != other.tupleNumber || id != other.id) {
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
            storage,
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
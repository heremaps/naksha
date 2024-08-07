@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.model.NakshaError.NakshaErrorCompanion.COLLECTION_NOT_FOUND
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
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
     * Reference to specific storage implementation that allows to decode rows to feature.
     */
    @JvmField val storage: IStorage,

    /**
     * The row-number, a unique identifier for the row.
     */
    @JvmField val tupleNumber: TupleNumber,

    /**
     * The metadata, this is going into the [XYZ namespace][XyzNs], when decoding the [Tuple] into a [NakshaFeature].
     */
    @JvmField val meta: Metadata,

    /**
     * Feature encoded with [FeatureEncoding] algorithm described by [Metadata.flags].
     */
    @JvmField val feature: ByteArray? = NOT_FETCHED,

    /**
     * Geometry encoded with [GeoEncoding] algorithm described by [Metadata.flags].
     * Might be _null_, when the feature does not have a geometry.
     */
    @JvmField val geo: ByteArray? = NOT_FETCHED,

    /**
     * Geometry-Reference-Point, encoded with the [GeoEncoding] algorithm described by [Metadata.flags].
     * Might be _null_, when the feature does not have a reference point.
     */
    @JvmField val referencePoint: ByteArray? = NOT_FETCHED,

    /**
     * Tags encoded with [TagsEncoding] algorithm described by [Metadata.flags].
     * Might be _null_, when the feature does not have any tags.
     */
    @JvmField val tags: ByteArray? = NOT_FETCHED,

    /**
     * An arbitrary binary attachment.
     */
    @JvmField val attachment: ByteArray? = NOT_FETCHED
) {
    companion object Tuple_C {
        /**
         * If the value has not yet been fetched from the database or any other source.
         */
        @JvmField
        @JsStatic
        val NOT_FETCHED = ByteArray(0)
    }

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
     * Maps row into a Naksha feature.
     * @return this row as Naksha feature.
     */
    fun toNakshaFeature(): NakshaFeature = storage.rowToFeature(this)

    private var guid: Guid? = null

    /**
     * Return the [Guid] for this row, requires that [meta] is not _null_, otherwise throws a [NakshaError.ILLEGAL_STATE].
     * @return the [Guid] of this row.
     */
    fun toGuid(): Guid {
        var g = guid
        if (g == null) {
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
     * Merge two rows into a new one. If this row is up-to-date, the method returns this row again.
     *
     * This is basically done, when more details become available about a row.
     *
     * - Throws [NakshaError.ILLEGAL_ARGUMENT], if the given row is not the same.
     * @param other the row to merge this with.
     * @return a new row, where nothing is _null_.
     */
    fun merge(other: Tuple): Tuple {
        if (storage != other.storage || tupleNumber != other.tupleNumber) {
            throw NakshaException(ILLEGAL_ARGUMENT, "Can't merge two different rows")
        }
        meta.nextVersion = meta.nextVersion ?: other.meta.nextVersion
        if (feature === other.feature
            && geo === other.geo
            && referencePoint === other.referencePoint
            && tags === other.tags
            && attachment === other.attachment
        ) return this
        return Tuple(
            storage, tupleNumber, meta,
            feature ?: other.feature,
            geo ?: other.geo,
            referencePoint ?: other.referencePoint,
            tags ?: other.tags,
            attachment ?: other.attachment
        )
    }

    /**
     * Tests if the tuple is fetched complete.
     * @return _true_, when the tuple is fully fetched; _false_ if parts are missing.
     */
    fun isComplete(): Boolean = feature !== NOT_FETCHED
            && geo !== NOT_FETCHED
            && referencePoint !== NOT_FETCHED
            && tags !== NOT_FETCHED
            && attachment !== NOT_FETCHED
}
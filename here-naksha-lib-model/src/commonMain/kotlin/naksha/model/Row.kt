package naksha.model

import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.objects.NakshaFeature
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * A row represents a specific immutable state of a feature in a storage.
 *
 * It is not required that the storage stores the information exactly in this form, this is only the exchange format. The row itself is immutable, so that it can be cached. The [id] is a unique identifier for the row in the [storage], [map] and collection with the set [collectionId].
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
data class Row(
    /**
     * Reference to specific storage implementation that allows to decode rows to feature.
     */
    @JvmField val storage: IStorage,

    /**
     * The map in which the row is located.
     */
    @JvmField val map: String,

    /**
     * The collection-id of the collection in which the row is located.
     */
    @JvmField val collectionId: String,

    /**
     * The row-identifier.
     */
    @JvmField val id: RowId,

    /**
     * The metadata, this is going into the [XYZ namespace][XyzNs], when decoding the [Row] into a [NakshaFeature].
     */
    @JvmField val meta: Metadata,

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
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = super.hashCode()

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
            g = Guid(storage.id(), map, collectionId, meta.id, meta.rowId())
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
    fun merge(other: Row): Row {
        if (storage != other.storage
            || map != other.map
            || collectionId != other.collectionId
            || id != other.id
            || meta != other.meta) throw NakshaException(ILLEGAL_ARGUMENT, "Can't merge two different rows")
        meta.nextVersion = meta.nextVersion ?: other.meta.nextVersion
        if (feature === other.feature
            && geo === other.geo
            && referencePoint === other.referencePoint
            && tags === other.tags
            && attachment === other.attachment) return this
        return Row(storage, map, collectionId, id, meta,
            feature ?: other.feature,
            geo ?: other.geo,
            referencePoint ?: other.referencePoint,
            tags ?: other.tags,
            attachment ?: other.attachment)
    }
}
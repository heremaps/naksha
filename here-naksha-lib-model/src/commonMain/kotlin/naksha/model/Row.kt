package naksha.model

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
    @JvmField val feature: ByteArray,

    /**
     * Geometry encoded with [GeoEncoding] algorithm described by [Metadata.flags].
     * Might be _null_, when the feature does not have a geometry.
     */
    @JvmField val geo: ByteArray?,

    /**
     * Geometry-Reference-Point, encoded with the [GeoEncoding] algorithm described by [Metadata.flags].
     * Might be _null_, when the feature does not have a reference point.
     */
    @JvmField
    val referencePoint: ByteArray?,

    /**
     * Tags encoded with [TagsEncoding] algorithm described by [Metadata.flags].
     * Might be _null_, when the feature does not have any tags.
     */
    @JvmField
    var tags: ByteArray?
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
}
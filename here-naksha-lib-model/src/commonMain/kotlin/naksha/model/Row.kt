package naksha.model

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * A row represents potentially all information stored about a feature in a storage.
 *
 * It is not required that the storage stores the information exactly in this form, this is only the exchange format.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
data class Row(
    /**
     * Reference to specific storage implementation that allows to decode rows to feature.
     */
    @JvmField
    val storage: IStorage,

    /**
     * The map in which the row is located.
     */
    @JvmField
    val map: String,

    /**
     * The collection-id of the collection in which the row is located.
     */
    @JvmField
    val collectionId: String,

    /**
     * The row-address in the collection.
     */
    @JvmField
    val addr: RowAddr,

    /**
     * The GUID (global unique identifier) of the feature.
     *
     * When features are written, the value can be provided by the client (using the [XyzNs]) to signal that an existing state was modified.
     */
    @JvmField
    val guid: Guid? = null,

    /**
     * Metadata, this is going into the [XYZ namespace][XyzNs], when decoding the [Row] into a [NakshaFeatureProxy].
     * In response might be _null_ when proper request flag was set.
     */
    @JvmField
    var meta: Metadata? = null,

    /**
     * Feature encoded with [FeatureEncoding] algorithm described by [flags].
     * In response might be _null_ when proper request flag was set.
     */
    @JvmField
    val feature: ByteArray? = null,

    /**
     * Geometry encoded with [GeoEncoding] algorithm described by [flags].
     * In response might be _null_ when proper request flag was set.
     */
    @JvmField
    val geo: ByteArray? = null,

    /**
     * Geometry-Reference-Point, encoded with the [GeoEncoding] algorithm described by [flags].
     * In response might be _null_ when proper request flag was set.
     */
    @JvmField
    val referencePoint: ByteArray? = null,

    /**
     * Tags encoded with [TagsEncoding] algorithm described by [flags].
     * In response might be _null_ when proper request flag was set.
     */
    @JvmField
    val tags: ByteArray? = null
) {
    /**
     * Maps row into memory model.
     */
    fun toMemoryModel(): NakshaFeatureProxy? {
        return storage.rowToFeature(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Row

        if (storage != other.storage) return false
        if (guid != other.guid) return false
        if (flags != other.flags) return false
        if (id != other.id) return false
        if (type != other.type) return false
        if (meta != other.meta) return false
        if (feature != null) {
            if (other.feature == null) return false
            if (!feature.contentEquals(other.feature)) return false
        } else if (other.feature != null) return false
        if (geo != null) {
            if (other.geo == null) return false
            if (!geo.contentEquals(other.geo)) return false
        } else if (other.geo != null) return false
        if (referencePoint != null) {
            if (other.referencePoint == null) return false
            if (!referencePoint.contentEquals(other.referencePoint)) return false
        } else if (other.referencePoint != null) return false
        if (tags != null) {
            if (other.tags == null) return false
            if (!tags.contentEquals(other.tags)) return false
        } else if (other.tags != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = storage.hashCode()
        result = 31 * result + (guid?.hashCode() ?: 0)
        result = 31 * result + flags
        result = 31 * result + id.hashCode()
        result = 31 * result + (type?.hashCode() ?: 0)
        result = 31 * result + (meta?.hashCode() ?: 0)
        result = 31 * result + (feature?.contentHashCode() ?: 0)
        result = 31 * result + (geo?.contentHashCode() ?: 0)
        result = 31 * result + (referencePoint?.contentHashCode() ?: 0)
        result = 31 * result + (tags?.contentHashCode() ?: 0)
        return result
    }
}
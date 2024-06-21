package naksha.model.response

import naksha.model.Guid
import naksha.model.IStorage
import naksha.model.NakshaFeatureProxy
import naksha.model.response.Metadata
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Represents the database Row layout.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
data class Row(
    /**
     * Reference to specific storage implementation allows row to feature conversion.
     */
    val storage: IStorage,
    /**
     * Flags to describe: feature, geometry, geo_ref, tags encodings and action flags.
     * @see Flags for more.
     */
    val flags: Int,
    /**
     * Feature id.
     */
    val id: String,
    /**
     * Guid of the feature, leave it empty for Insert operation (it will be calculated automatically).
     */
    val guid: Guid? = null,
    /**
     * Feature type, null - indicates the type is default for collection.
     */
    val type: String? = null,
    /**
     * Metadata.
     * In response might be null when proper request flag was set.
     */
    var meta: Metadata? = null,
    /**
     * Feature encoded with algorithm described by flags.
     * In response might be null when proper request flag was set.
     */
    val feature: ByteArray? = null,
    /**
     * Geometry encoded with algorithm described by flags.
     * In response might be null when proper request flag was set.
     */
    val geo: ByteArray? = null,
    /**
     * GeoRef encoded with algorithm described by flags.
     * In response might be null when proper request flag was set.
     */
    val geoRef: ByteArray? = null,
    /**
     * Tags encoded with algorithm described by flags.
     * In response might be null when proper request flag was set.
     */
    val tags: ByteArray? = null
) {
    /**
     * Maps row into memory model.
     */
    fun toMemoryModel(): NakshaFeatureProxy? {
        return storage.convertRowToFeature(this)
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
        if (geoRef != null) {
            if (other.geoRef == null) return false
            if (!geoRef.contentEquals(other.geoRef)) return false
        } else if (other.geoRef != null) return false
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
        result = 31 * result + (geoRef?.contentHashCode() ?: 0)
        result = 31 * result + (tags?.contentHashCode() ?: 0)
        return result
    }
}
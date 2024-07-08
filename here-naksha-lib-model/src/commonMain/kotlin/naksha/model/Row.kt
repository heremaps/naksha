package naksha.model

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * A row represents all information stored about a feature in a storage. It is not required that the storage stores the information
 * exactly in this form, this is only the exchange format.
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
     * The feature id.
     */
    @JvmField
    val id: String,

    /**
     * The GUID (global unique identifier) of the feature. When features are written, the value can be provided by the client (using the
     * [XyzProxy]) to signal that an existing state was modified.
     */
    @JvmField
    val guid: Guid? = null,

    /**
     * Feature type, extracted from `properties.featureType`, if this is no string, then `type` from the root is used, which normally is
     * always `Feature` for _Geo-JSON_ features. The value _null_ indicates the type is the collection default type, it saves a lot of
     * storage space, when all features in a collection are of the same type, to encode the type in the collection, when creating the
     * collection. Beware, the default feature-type of a collection is an immutable property!
     */
    @JvmField
    val type: String? = null,

    /**
     * Metadata, this is going into the [XYZ namespace][XyzProxy], when decoding the [Row] into a [NakshaFeatureProxy].
     * In response might be _null_ when proper request flag was set.
     */
    @JvmField
    var meta: Metadata? = null,

    /**
     * The flags of the row, this bitmask stores how the geometry, reference-point, feature and tags are encoded, as well as the action.
     * @see GeoEncoding
     * @see FeatureEncoding
     * @see TagsEncoding
     * @see Action
     */
    @JvmField
    val flags: Flags,

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
    val geoRef: ByteArray? = null,

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
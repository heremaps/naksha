package naksha.model.response

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
class Row(
    /**
     * Reference to specific storage implementation allows row to feature conversion.
     */
    val storage: IStorage,
    /**
     * Guid of the feature, leave it empty for Insert operation (it will be calculated automatically).
     */
    val guid: naksha.model.Guid?,
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
     * Feature type, null - indicates the type is default for collection.
     */
    val type: String? = null,
    /**
     * Metadata.
     * In response might be null when proper request flag was set.
     */
    val meta: Metadata? = null,
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
    fun toMemoryModel(): NakshaFeatureProxy {
        throw NotImplementedError("implement conversion")
    }
}
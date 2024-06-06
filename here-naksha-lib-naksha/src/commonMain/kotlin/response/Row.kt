package com.here.naksha.lib.base.response

import com.here.naksha.lib.base.Guid
import com.here.naksha.lib.base.IStorage
import com.here.naksha.lib.base.P_NakshaFeature
import com.here.naksha.lib.nak.Flags
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
    val guid: Guid?,
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
    fun toMemoryModel(): P_NakshaFeature {
        throw NotImplementedError("implement conversion")
    }
}
@file:Suppress("OPT_IN_USAGE")

package naksha.model

import kotlin.js.JsExport

/**
 * An interface to an array of binary encoded metadata.
 */
@JsExport
interface IMetadataArray {
    /**
     * The amount of entries.
     */
    val size: Int

    /**
     * Returns the metadata at the given index.
     */
    operator fun get(index: Int): Metadata
}
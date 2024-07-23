@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.model.Row
import naksha.model.NakshaFeatureProxy
import naksha.model.response.ExecutedOp
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * A result row as returned by the storage.
 * @property op the operation that was executed.
 * @property row the immutable row as returned by the storage, can be _null_ for [ExecutedOp.PURGED] or [ExecutedOp.RETAINED].
 */
@JsExport
data class ResultRow(
    @JvmField
    val op: ExecutedOp,
    @JvmField
    val row: Row?
) {
    private var feature: NakshaFeatureProxy? = null

    /**
     * Convert the row into a feature and cache the feature.
     *
     * Beware: If the returned feature is modified, this will as well modify the feature cached in the row.
     * @return the row converted into a feature, cached in this result-row.
     */
    fun getFeature(): NakshaFeatureProxy? {
        if (feature == null) feature = row?.toMemoryModel()
        return feature
    }

    /**
     * Convert the row into a feature, do not cache.
     *
     * @return a new copy of the row converted into a feature.
     */
    fun newFeature(): NakshaFeatureProxy? = row?.toMemoryModel()
}


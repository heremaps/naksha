@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.model.request.*
import kotlin.js.JsExport

/**
 * A read-only session.
 */
@JsExport
interface IReadSession: ISession {
    /**
     * Helper method to quickly read a single feature from the storage.
     * @param id the identifier of the feature to read.
     * @return the read feature, if such a feature exists; _null_ otherwise.
     * @since 3.0.0
     */
    fun getFeatureById(id: String): ResultRow?

    /**
     * Helper method to quickly read a set of features from the storage.
     * @param ids the identifiers of the features to read.
     * @return a map that contains all features that where read.
     * @since 3.0.0
     */
    fun getFeaturesByIds(vararg ids: String): Map<String, ResultRow>
}

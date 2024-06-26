package naksha.model

import naksha.model.response.Response
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A write-request.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
interface IWriteSession: IReadSession {

    /**
     * Helper to write a single feature.
     * @param feature the feature to write.
     * @return the response.
     */
    fun writeFeature(feature: NakshaFeatureProxy): Response

    /**
     * Commit all pending changes in the current transaction. Returns the underlying connection back into the connection pool.
     */
    fun commit()

    /**
     * Rollback (revert) all pending changes in the current transaction. Returns the underlying connection back into the connection pool.
     */
    fun rollback()
}
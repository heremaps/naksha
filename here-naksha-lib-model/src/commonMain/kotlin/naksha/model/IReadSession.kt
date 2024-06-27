package naksha.model

import naksha.model.response.Response
import naksha.model.request.*
import kotlin.js.JsExport

/**
 * A read-only session.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
interface IReadSession: ISession {

    /**
     * Execute the given [Request]. The read-only session will only be able to execute
     * [ReadRequest]'s.
     * @param request the request to execute.
     * @return the response.
     */
    fun execute(request: Request): Response

    /**
     * Execute the given [Request] in parallel, if possible. For [WriteRequest] this method is performing a form of auto-commit. This
     * means, all writes are executed in parallel and when all requests are successfully done, all are committed, if any error happens,
     * all are auto-rolled back.
     *
     * **Warning**: There is a minor risk to create a broken state in the storage! Even after all requests have been executed
     * successfully, committing may fail partially, for example when only one connection aborts or the server crashes in the middle of
     * the operation, while having committed already some chunks, with others not yet done.
     */
    fun executeParallel(request: Request): Response

    /**
     * Helper method to quickly read a single feature from the storage.
     * @param id the identifier of the feature to read.
     * @return the read feature, if such a feature exists; _null_ otherwise.
     */
    fun getFeatureById(id: String): ResultRow?

    /**
     * Helper method to quickly read a set of features from the storage.
     * @param ids the identifiers of the features to read.
     * @return a map that contains all features that where read.
     */
    fun getFeaturesByIds(ids: List<String>): Map<String, ResultRow>
}

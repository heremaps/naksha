package naksha.model.request

import naksha.model.Guid
import naksha.model.NakshaCollectionProxy
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Update operation, if you picked this operation but collection doesn't exist in DB, the error will be returned - for such cases use WriteCollection.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class UpdateCollection(
    collection: NakshaCollectionProxy,
    /**
     * Indicates if operation should verify `uuid` of the collection stored in DB.
     * true - will perform Update only if `uuid` of the collection in request matches `uuid` of the collection in DB. Response with error if not.
     * false - operation will be performed for collection with same `id` without other verification
     *
     * Default: false
     */
    atomic: Boolean = false,
) : UpdateFeature("naksha~collections", collection, atomic)

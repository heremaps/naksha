package naksha.model.request

import naksha.model.Guid

/**
 * Delete operation, if uuid is specified, the executor will verify if collection on DB has the same uuid and perform operation only when it is.
 */
class DeleteCollection (
    /**
     * ID of the collection to delete.
     */
    id: String,
    guid: Guid? = null
) : DeleteFeature(ADMIN_COLLECTION_ID, id, guid)
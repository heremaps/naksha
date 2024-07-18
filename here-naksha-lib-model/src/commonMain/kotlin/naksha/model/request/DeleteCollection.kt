package naksha.model.request

import naksha.model.Guid

/**
 * Delete operation, if uuid is specified, the executor will verify if collection on DB has the same uuid and perform operation only when it is.
 */
class DeleteCollection (
    collectionId: String,
    guid: Guid? = null
) : Write(XYZ_OP_DELETE,collectionId) {
    override fun getId(): String = collectionId
}
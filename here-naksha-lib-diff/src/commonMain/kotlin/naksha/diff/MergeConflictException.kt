package naksha.diff

/**
 * An exception thrown if applying a patch fails, the creation of a difference fails or any other merge error occurs.
 */
class MergeConflictException(msg: String) : Exception(msg)



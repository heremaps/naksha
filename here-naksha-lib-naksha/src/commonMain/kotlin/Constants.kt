package com.here.naksha.lib.base


const val ACTION_CREATE = 0
const val ACTION_UPDATE = 1
const val ACTION_DELETE = 2


val XYZ_OP_NAME = arrayOf("CREATE", "UPDATE", "UPSERT", "DELETE", "PURGE")

// Feature was read.
const val XYZ_EXEC_READ = "READ"

// Feature was created.
const val XYZ_EXEC_CREATED = "CREATED"

// Feature was updated.
const val XYZ_EXEC_UPDATED = "UPDATED"

// Feature was deleted.
const val XYZ_EXEC_DELETED = "DELETED"

// Feature was purged.
const val XYZ_EXEC_PURGED = "PURGED"

// Feature did not change, returns current state, which may be null!
const val XYZ_EXEC_RETAINED = "RETAINED"

// Operation failed.
const val XYZ_EXEC_ERROR = "ERROR"
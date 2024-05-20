package com.here.naksha.lib.base


const val ACTION_CREATE = 0
const val ACTION_UPDATE = 1
const val ACTION_DELETE = 2

const val XYZ_OP_CREATE = 0
const val XYZ_OP_UPDATE = 1
const val XYZ_OP_UPSERT = 2 // aka PUT
const val XYZ_OP_DELETE = 3
const val XYZ_OP_PURGE = 4
val XYZ_OP_NAME = arrayOf("CREATE", "UPDATE", "UPSERT", "DELETE", "PURGE")
val XYZ_OP_INT = arrayOf(XYZ_OP_CREATE, XYZ_OP_UPDATE, XYZ_OP_UPSERT, XYZ_OP_DELETE, XYZ_OP_PURGE)

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
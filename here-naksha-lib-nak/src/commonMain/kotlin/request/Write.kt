package com.here.naksha.lib.base.request

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Basic write operation
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class Write(
    /**
     *  Possible values: INSERT/CREATE (0), UPDATE (1), WRITE/UPSERT/PUT (2), DELETE (3), PURGE (4)
     */
    val op: Int,
    /**
     * head collection name, if the head is partitioned it shouldn't have partition suffix.
     */
    val collectionId: String
) {

    /**
     * Returns Feature (object) id.
     */
    abstract fun getId(): String

    companion object {
        const val XYZ_OP_CREATE = 0
        const val XYZ_OP_UPDATE = 1
        const val XYZ_OP_UPSERT = 2 // aka PUT
        const val XYZ_OP_DELETE = 3
        const val XYZ_OP_PURGE = 4
    }
}
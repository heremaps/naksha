@file:Suppress("OPT_IN_USAGE")
package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmStatic

@JsExport
class P_TransactionCollectionInfo  : P_Object() {

    companion object {
        @JvmStatic
        val COLLECTION_ID = Platform.intern("collectionId")

        @JvmStatic
        val INSERTED = Platform.intern("inserted")

        @JvmStatic
        val INSERTED_BYTES = Platform.intern("insertedBytes")

        @JvmStatic
        val UPDATED = Platform.intern("updated")

        @JvmStatic
        val UPDATED_BYTES = Platform.intern("updatedBytes")

        @JvmStatic
        val DELETED = Platform.intern("deleted")

        @JvmStatic
        val DELETED_BYTES = Platform.intern("deletedBytes")

        @JvmStatic
        val PURGED = Platform.intern("purged")

        @JvmStatic
        val PURGED_BYTES = Platform.intern("purgedBytes")
    }

    fun getCollectionId(): String? = getOrNull(COLLECTION_ID, Platform.stringKlass)

    fun setCollectionId(collectionId: String) = set(COLLECTION_ID, collectionId)

    fun getInserted(): Int = getOrCreate(INSERTED, Platform.intKlass)

    fun setInserted(inserted: Int) = set(INSERTED_BYTES, inserted)

    fun getUpdated(): Int = getOrCreate(UPDATED, Platform.intKlass)

    fun setUpdated(updated: Int) = set(UPDATED_BYTES, updated)

    fun getDeleted(): Int = getOrCreate(DELETED, Platform.intKlass)

    fun setDeleted(deleted: Int) = set(DELETED_BYTES, deleted)

    fun getDeletedBytes(): Int = getOrCreate(DELETED_BYTES, Platform.intKlass)

    fun setDeletedBytes(deleted: Int) = set(DELETED_BYTES, deleted)

    fun getUpdatedBytes(): Int = getOrCreate(UPDATED_BYTES, Platform.intKlass)

    fun setUpdatedBytes(updated: Int) = set(UPDATED_BYTES, updated)

    fun getPurged(): Int = getOrCreate(PURGED, Platform.intKlass)

    fun setPurged(purged: Int) = set(PURGED_BYTES, purged)

    fun getPurgedBytes(): Int = getOrCreate(PURGED_BYTES, Platform.intKlass)

    fun setPurgedBytes(purged: Int) = set(PURGED_BYTES, purged)
}
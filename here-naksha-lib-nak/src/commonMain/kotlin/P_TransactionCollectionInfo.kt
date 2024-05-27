@file:Suppress("OPT_IN_USAGE")
package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmStatic

@JsExport
class P_TransactionCollectionInfo  : BaseObject() {

    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<P_TransactionCollectionInfo>() {
            override fun isInstance(o: Any?): Boolean = o is P_TransactionCollectionInfo

            override fun newInstance(vararg args: Any?): P_TransactionCollectionInfo = P_TransactionCollectionInfo()
        }

        @JvmStatic
        val COLLECTION_ID = Base.intern("collectionId")

        @JvmStatic
        val INSERTED = Base.intern("inserted")

        @JvmStatic
        val INSERTED_BYTES = Base.intern("insertedBytes")

        @JvmStatic
        val UPDATED = Base.intern("updated")

        @JvmStatic
        val UPDATED_BYTES = Base.intern("updatedBytes")

        @JvmStatic
        val DELETED = Base.intern("deleted")

        @JvmStatic
        val DELETED_BYTES = Base.intern("deletedBytes")

        @JvmStatic
        val PURGED = Base.intern("purged")

        @JvmStatic
        val PURGED_BYTES = Base.intern("purgedBytes")
    }

    fun getCollectionId(): String? = getOrNull(COLLECTION_ID, Klass.stringKlass)

    fun setCollectionId(collectionId: String) = set(COLLECTION_ID, collectionId)

    fun getInserted(): Int = getOrCreate(INSERTED, Klass.intKlass)

    fun setInserted(inserted: Int) = set(INSERTED_BYTES, inserted)

    fun getUpdated(): Int = getOrCreate(UPDATED, Klass.intKlass)

    fun setUpdated(updated: Int) = set(UPDATED_BYTES, updated)

    fun getDeleted(): Int = getOrCreate(DELETED, Klass.intKlass)

    fun setDeleted(deleted: Int) = set(DELETED_BYTES, deleted)

    fun getDeletedBytes(): Int = getOrCreate(DELETED_BYTES, Klass.intKlass)

    fun setDeletedBytes(deleted: Int) = set(DELETED_BYTES, deleted)

    fun getUpdatedBytes(): Int = getOrCreate(UPDATED_BYTES, Klass.intKlass)

    fun setUpdatedBytes(updated: Int) = set(UPDATED_BYTES, updated)

    fun getPurged(): Int = getOrCreate(PURGED, Klass.intKlass)

    fun setPurged(purged: Int) = set(PURGED_BYTES, purged)

    fun getPurgedBytes(): Int = getOrCreate(PURGED_BYTES, Klass.intKlass)

    fun setPurgedBytes(purged: Int) = set(PURGED_BYTES, purged)
}
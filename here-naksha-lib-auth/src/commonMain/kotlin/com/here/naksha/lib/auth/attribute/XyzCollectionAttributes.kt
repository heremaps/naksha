package com.here.naksha.lib.auth.attribute

class XyzCollectionAttributes(vararg args: Any) : CommonAttributes<XyzCollectionAttributes>(*args) {

    fun storageId(storageId: String) = apply { set(STORAGE_ID_KEY, storageId) }

    fun storageTags(storageTags: List<String>) = apply { set(STORAGE_TAGS_KEY, storageTags) }

    companion object {
        const val STORAGE_ID_KEY = "storageId"
        const val STORAGE_TAGS_KEY = "storageTags"
    }
}
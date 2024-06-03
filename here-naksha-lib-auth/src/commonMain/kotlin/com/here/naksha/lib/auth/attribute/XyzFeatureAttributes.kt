package com.here.naksha.lib.auth.attribute

import com.here.naksha.lib.base.P_List

class XyzFeatureAttributes(vararg args: Any) : CommonAttributes<XyzFeatureAttributes>(*args) {

    fun storageId(storageId: String) = apply { set(STORAGE_ID_KEY, storageId) }

    fun storageTags(storageTags: List<String>) = apply {
        box(storageTags, P_List::class)?.let {
            set(STORAGE_TAGS_KEY, it)
        }
    }

    fun collectionId(collectionId: String) = apply { set(COLLECTION_ID_KEY, collectionId) }

    fun collectionTags(collectionTags: List<String>) = apply {
        box(collectionTags, P_List::class)?.let {
            set(COLLECTION_TAGS_KEY, it)
        }
    }

    companion object {
        const val STORAGE_ID_KEY = "storageId"
        const val STORAGE_TAGS_KEY = "storageTags"
        const val COLLECTION_ID_KEY = "collectionId"
        const val COLLECTION_TAGS_KEY = "collectionTags"
    }
}

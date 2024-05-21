package com.here.naksha.lib.auth.attribute

import com.here.naksha.lib.auth.toBaseArray

class XyzFeatureAttributes(vararg args: Any): CommonAttributes<XyzFeatureAttributes>(*args) {

    fun storageId(storageId: String) = apply { set(STORAGE_ID_KEY, storageId) }

    fun storageTags(storageTags: List<String>) = apply { set(STORAGE_TAGS_KEY, storageTags.toBaseArray()) }

    fun collectionId(collectionId:String) = apply { set(COLLECTION_ID_KEY, collectionId) }

    fun collectionTags(collectionTags: List<String>) = apply { set(COLLECTION_TAGS_KEY, collectionTags.toBaseArray()) }

    companion object {
        const val STORAGE_ID_KEY = "storageId"
        const val STORAGE_TAGS_KEY = "storageTags"
        const val COLLECTION_ID_KEY = "collectionId"
        const val COLLECTION_TAGS_KEY = "collectionTags"
    }
}

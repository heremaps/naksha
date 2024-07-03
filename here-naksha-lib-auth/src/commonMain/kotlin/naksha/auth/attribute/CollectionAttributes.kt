package naksha.auth.attribute

import kotlin.js.JsExport

@JsExport
class CollectionAttributes : NakshaAttributes<CollectionAttributes>() {

    fun storageId(storageId: String) = apply { set(STORAGE_ID_KEY, storageId) }

    fun storageTags(storageTags: List<String>) = apply { set(STORAGE_TAGS_KEY, storageTags) }

    companion object {
        const val STORAGE_ID_KEY = "storageId"
        const val STORAGE_TAGS_KEY = "storageTags"
    }
}
package naksha.auth.attribute

import naksha.base.AbstractListProxy
import kotlin.js.JsExport


@JsExport
class FeatureAttributes : NakshaAttributes<FeatureAttributes>() {

    fun storageId(storageId: String) = apply { set(STORAGE_ID_KEY, storageId) }

    fun storageTags(storageTags: List<String>) = apply {
        box(storageTags, AbstractListProxy::class)?.let {
            set(STORAGE_TAGS_KEY, it)
        }
    }

    fun collectionId(collectionId: String) = apply { set(COLLECTION_ID_KEY, collectionId) }

    fun collectionTags(collectionTags: List<String>) = apply {
        box(collectionTags, AbstractListProxy::class)?.let {
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

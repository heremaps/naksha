@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.AnyObject
import kotlin.js.JsExport

@JsExport
class TransactionCollectionInfoProxy : AnyObject() {

    companion object {
        private val COLLECTION_ID = NullableProperty<Any, TransactionCollectionInfoProxy, String>(String::class)
        private val COUNT = NotNullProperty<Any, TransactionCollectionInfoProxy, Int>(Int::class) { _,_ -> 0 }
    }

    var collectionId: String? by COLLECTION_ID
    var inserted: Int by COUNT
    var updated: Int by COUNT
    var deleted: Int by COUNT
    var purged: Int by COUNT
    var insertedBytes: Int by COUNT
    var updatedBytes: Int by COUNT
    var deletedBytes: Int by COUNT
    var purgedBytes: Int by COUNT

    fun addValues(counts: TransactionCollectionInfoProxy) {
        check(collectionId == counts.collectionId)
        this.inserted += counts.inserted
        this.updated += counts.updated
        this.deleted += counts.deleted
        this.purged += counts.purged
        this.insertedBytes += counts.insertedBytes
        this.updatedBytes += counts.updatedBytes
        this.deletedBytes += counts.deletedBytes
        this.purgedBytes += counts.purgedBytes
    }
}
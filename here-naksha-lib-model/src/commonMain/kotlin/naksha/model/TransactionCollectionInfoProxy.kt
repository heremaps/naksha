@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.P_Object
import kotlin.js.JsExport

@JsExport
class TransactionCollectionInfoProxy : P_Object() {

    companion object {
        private val COLLECTION_ID = NullableProperty<Any, TransactionCollectionInfoProxy, String>(String::class)
        private val COUNT = NotNullProperty<Any, TransactionCollectionInfoProxy, Int>(Int::class)
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
}
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

    var collectionId: String? by TransactionCollectionInfoProxy.Companion.COLLECTION_ID
    var inserted: Int by TransactionCollectionInfoProxy.Companion.COUNT
    var updated: Int by TransactionCollectionInfoProxy.Companion.COUNT
    var deleted: Int by TransactionCollectionInfoProxy.Companion.COUNT
    var purged: Int by TransactionCollectionInfoProxy.Companion.COUNT
    var insertedBytes: Int by TransactionCollectionInfoProxy.Companion.COUNT
    var updatedBytes: Int by TransactionCollectionInfoProxy.Companion.COUNT
    var deletedBytes: Int by TransactionCollectionInfoProxy.Companion.COUNT
    var purgedBytes: Int by TransactionCollectionInfoProxy.Companion.COUNT
}
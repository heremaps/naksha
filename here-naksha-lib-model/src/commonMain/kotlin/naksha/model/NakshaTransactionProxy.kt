@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.base.NotNullProperty
import kotlin.js.JsExport

@JsExport
class NakshaTransactionProxy() : NakshaFeatureProxy() {

    companion object {
        private val FEATURES_MODIFIED =
            NotNullProperty<Any, NakshaTransactionProxy, Int>(Int::class, defaultValue = { 0 })
        private val FEATURES_BYTES = NotNullProperty<Any, NakshaTransactionProxy, Int>(Int::class, defaultValue = { 0 })
        private val COLLECTIONS = NotNullProperty<Any, NakshaTransactionProxy, TransactionCollectionInfoMapProxy>(
            TransactionCollectionInfoMapProxy::class, defaultValue = { TransactionCollectionInfoMapProxy() }
        )
        private val SEQ_NUMBER = NotNullProperty<Any, NakshaTransactionProxy, Int64>(Int64::class)
        private val SEQ_TS = NotNullProperty<Any, NakshaTransactionProxy, Int64>(Int64::class)
    }

    /**
     * Number of features modified in the transaction - total number of features from all touched collections.
     */
    var featuresModified: Int by FEATURES_MODIFIED

    /**
     * Total number of bytes sent to DB in transaction.
     */
    var featuresBytes: Int by FEATURES_BYTES

    /**
     * The seqNumber of the transaction is a sequential number starting with 1 for the first transaction, it has no holes and is generated by a sequencer.
     * Therefore, transactions that have not been sequenced yet have no seqNumber (null) or seqTs (null).
     */
    var seqNumber: Int64 by SEQ_NUMBER

    var seqTs: Int64 by SEQ_TS

    var collections: TransactionCollectionInfoMapProxy by COLLECTIONS


    fun incFeaturesModified(count: Int) {
        featuresModified += count
    }

    fun addCollectionCounts(collectionCounts: TransactionCollectionInfoProxy) {
        if (!collections.contains(collectionCounts.collectionId)) {
            collections[collectionCounts.collectionId!!] = collectionCounts
        } else {
            collections[collectionCounts.collectionId]!!.addValues(collectionCounts)
        }

    }
}
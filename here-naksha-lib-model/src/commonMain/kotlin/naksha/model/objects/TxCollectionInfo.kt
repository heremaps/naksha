@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.NotNullProperty
import naksha.base.AnyObject
import naksha.model.NakshaError
import naksha.model.NakshaException
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * An object storing detailed information what changed in a specific collection within a transaction.
 */
@JsExport
class TxCollectionInfo() : AnyObject() {

    /**
     * Create a new collection info.
     * @param collectionId the collection identifier.
     */
    @JsName("of")
    constructor(collectionId: String) : this() {
        this.collectionId = collectionId
    }

    companion object {
        private val STRING = NotNullProperty<TxCollectionInfo, String>(String::class) { _, _ -> "" }
        private val COUNT = NotNullProperty<TxCollectionInfo, Int>(Int::class) { _, _ -> 0 }
    }

    var collectionId by STRING
    var inserted: Int by COUNT
    var updated: Int by COUNT
    var deleted: Int by COUNT
    var purged: Int by COUNT
    var insertedBytes: Int by COUNT
    var updatedBytes: Int by COUNT
    var deletedBytes: Int by COUNT
    var purgedBytes: Int by COUNT

    fun addValues(counts: TxCollectionInfo) {
        if (collectionId != counts.collectionId) {
            throw NakshaException(NakshaError.ILLEGAL_ARGUMENT,
                "The given 'counts' is for another collection: ${counts.collectionId}")
        }
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
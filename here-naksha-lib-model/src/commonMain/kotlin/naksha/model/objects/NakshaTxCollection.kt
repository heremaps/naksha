@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.NotNullProperty
import naksha.base.AnyObject
import naksha.model.Action
import naksha.model.NakshaError
import naksha.model.NakshaException
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * An object storing detailed information what changed in a specific collection within a transaction.
 * @since 3.0
 */
@JsExport
class NakshaTxCollection() : AnyObject() {

    /**
     * Create a new collection info.
     * @param id the collection identifier.
     */
    @JsName("of")
    constructor(id: String, number: Int, action: Action) : this() {
        this.id = id
        this.number = number
        this.action = action
    }

    companion object {
        private val ID = NotNullProperty<NakshaTxCollection, String>(String::class) { _, _ -> "" }
        private val NUMBER = NotNullProperty<NakshaTxCollection, Int>(Int::class) { _, _ -> 0 }
        private val ACTION = NotNullProperty<NakshaTxCollection, Action>(Action::class) { _, _ -> Action.UNDEFINED }
        private val COUNT = NotNullProperty<NakshaTxCollection, Int>(Int::class) { _, _ -> 0 }
    }

    var id by ID
    var number by NUMBER
    var action by ACTION
    var inserted: Int by COUNT
    var updated: Int by COUNT
    var deleted: Int by COUNT
    var purged: Int by COUNT
    var insertedBytes: Int by COUNT
    var updatedBytes: Int by COUNT
    var deletedBytes: Int by COUNT
    var purgedBytes: Int by COUNT

    fun addValues(counts: NakshaTxCollection) {
        if (id != counts.id) {
            throw NakshaException(NakshaError.ILLEGAL_ARGUMENT,
                "The given 'counts' is for another collection: ${counts.id}")
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
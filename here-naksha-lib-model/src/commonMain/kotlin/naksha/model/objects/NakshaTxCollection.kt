@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.NotNullProperty
import naksha.base.AnyObject
import naksha.base.NullableProperty
import naksha.model.Action
import naksha.model.Action.Action_C.CREATED
import naksha.model.Action.Action_C.DELETED
import naksha.model.Action.Action_C.UPDATED
import naksha.model.NakshaError
import naksha.model.NakshaException
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmOverloads

/**
 * An object storing detailed information what changed in a specific collection within a transaction.
 * @since 3.0
 */
@JsExport
class NakshaTxCollection() : AnyObject() {

    /**
     * Create a new collection info.
     * @param id the collection-id.
     * @param number the collection-number.
     * @param action the _(optional)_ action done to this collection; `null` if the collection was not modified.
     */
    @JvmOverloads
    @JsName("of")
    constructor(id: String, number: Int, action: Action? = null) : this() {
        this.id = id
        this.number = number
        this.action = action
    }

    companion object {
        private val ID = NotNullProperty<NakshaTxCollection, String>(String::class)
        private val NUMBER = NotNullProperty<NakshaTxCollection, Int>(Int::class)
        private val ACTION_OR_NULL = NullableProperty<NakshaTxCollection, Action>(Action::class)
        private val COUNT = NotNullProperty<NakshaTxCollection, Int>(Int::class) { _, _ -> 0 }
    }

    /**
     * The collection-id.
     * @since 3.0
     */
    var id by ID

    /**
     * The collection-number.
     * @since 3.0
     */
    var number by NUMBER

    /**
     * The [Action] done to the collection, if the collection was modified within the transaction, `null` if only children _(features)_ of the collection were modified.
     * @since 3.0
     */
    var action by ACTION_OR_NULL

    /**
     * The amount of features that have been created.
     * @since 3.0
     */
    var created: Int by COUNT

    /**
     * The amount of features that have been updated.
     * @since 3.0
     */
    var updated: Int by COUNT

    /**
     * The amount of features that have been deleted.
     * @since 3.0
     */
    var deleted: Int by COUNT

    /**
     * The amount of bytes of the features that have been inserted.
     * @since 3.0
     */
    var createdBytes: Int by COUNT

    /**
     * The amount of bytes of the features that have been updated.
     * @since 3.0
     */
    var updatedBytes: Int by COUNT

    /**
     * Helper method to add one change.
     * @param action the action that was done, only [CREATED], [UPDATED], and [DELETED] are counted.
     * @return this.
     */
    fun add(action: Action): NakshaTxCollection {
        when (action) {
            CREATED -> this.created += 1
            UPDATED -> this.updated += 1
            DELETED -> this.deleted += 1
        }
        return this
    }

    /**
     * Helper method to add all counts of the other collection into this one.
     *
     * The method adds the following values to the ones of this:
     * - [created]
     * - [updated]
     * - [deleted]
     * - [createdBytes]
     * - [updatedBytes]
     * @param counts the other collection from which to add the counts.
     * @return this.
     */
    fun addValues(counts: NakshaTxCollection): NakshaTxCollection {
        if (id != counts.id) {
            throw NakshaException(NakshaError.ILLEGAL_ARGUMENT,
                "The given 'counts' is for another collection: ${counts.id}")
        }
        this.created += counts.created
        this.updated += counts.updated
        this.deleted += counts.deleted
        this.createdBytes += counts.createdBytes
        this.updatedBytes += counts.updatedBytes
        return this
    }
}
@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.AnyObject
import naksha.base.NotNullProperty
import naksha.model.Action
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * An information about what changes in this map.
 * @since 3.0
 */
@JsExport
class NakshaTxMap() : AnyObject() {

    @JsName("of")
    constructor(id: String, number: Int, action: Action) : this() {
        this.id = id
        this.number = number
        this.action = action
    }

    companion object NakshaTxMap_C {
        private val MAP_ID = NotNullProperty<NakshaTxMap, String>(String::class) { _, _ -> "" }
        private val MAP_NUMBER = NotNullProperty<NakshaTxMap, Int>(Int::class) { _, _ -> 0 }
        private val ACTION = NotNullProperty<NakshaTxMap, Action>(Action::class) { _, _ -> Action.UNDEFINED }
        private val COLLECTIONS = NotNullProperty<NakshaTxMap, NakshaTxCollectionById>(NakshaTxCollectionById::class)
    }

    /**
     * The map-id of this map.
     * @since 3.0
     */
    var id by MAP_ID

    /**
     * The map-number of this map.
     * @since 3.0
     */
    var number by MAP_NUMBER

    /**
     * The action done to this map.
     * @since 3.0
     */
    var action by ACTION

    /**
     * The collections of this map that have been modified as part of this transaction.
     * @since 3.0
     */
    var collections by COLLECTIONS

    /**
     * Returns the collection-info of the given collection, if it does not yet exist, creates a new empty one.
     * @param id the collection-id.
     * @param number the collection-number to use.
     * @param action the action being done to the collection.
     * @return the transaction collection information.
     * @since 3.0
     */
    fun useTxCollection(id: String, number: Int, action: Action): NakshaTxCollection {
        var info = collections[id]
        if (info == null) {
            info = NakshaTxCollection(id, number, action)
            collections[id] = info
        }
        return info
    }

    /**
     * Add the given collection info, if it exists already, otherwise a new entry is created, and the given values are added.
     * @param info the collection info to add.
     * @since 3.0
     */
    fun addTxCollectionInfo(info: NakshaTxCollection) {
        val existing = collections[info.id]
        if (existing != null) existing.addValues(info) else collections[info.id] = info
    }
}
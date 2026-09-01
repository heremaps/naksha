@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.AnyObject
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.Action
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmOverloads

/**
 * An information about what changes in this map.
 * @since 3.0
 */
@JsExport
class NakshaTxMap() : AnyObject() {

    @JvmOverloads
    @JsName("of")
    constructor(id: String, number: Int, action: Action? = null) : this() {
        this.id = id
        this.number = number
        this.action = action
    }

    companion object NakshaTxMap_C {
        private val ID = NotNullProperty<NakshaTxMap, String>(String::class)
        private val NUMBER = NotNullProperty<NakshaTxMap, Int>(Int::class)
        private val ACTION_OR_NULL = NullableProperty<NakshaTxMap, Action>(Action::class)
        private val COLLECTIONS = NotNullProperty<NakshaTxMap, NakshaTxCollectionById>(NakshaTxCollectionById::class)
    }

    /**
     * The map-id of this map.
     * @since 3.0
     */
    var id by ID

    /**
     * The map-number of this map.
     * @since 3.0
     */
    var number by NUMBER

    /**
     * The action done to this map, `null` if just children of the map where modified.
     * @since 3.0
     */
    var action by ACTION_OR_NULL

    /**
     * The collections of this map that have been modified as part of this transaction.
     * @since 3.0
     */
    var collections by COLLECTIONS

    /**
     * Returns the collection-info of the given collection, if it does not yet exist, creates a new empty one.
     * @param id the collection-id.
     * @param number the collection-number to use.
     * @param action the action being done to the collection, `null` if only children were modified.
     * @return the transaction collection information.
     * @since 3.0
     */
    @JvmOverloads
    fun useCollection(id: String, number: Int, action: Action? = null): NakshaTxCollection {
        var info = collections[id]
        if (info != null) {
            if (action != null) info.action = action
            return info
        }
        info = NakshaTxCollection(id, number, action)
        collections[id] = info
        return info
    }

    /**
     * Add the given collection info, if it exists already, otherwise a new entry is created, and the given values are added.
     * @param info the collection info to add.
     * @since 3.0
     */
    fun addCollection(info: NakshaTxCollection) {
        val existing = collections[info.id]
        if (existing != null) existing.addValues(info) else collections[info.id] = info
    }
}
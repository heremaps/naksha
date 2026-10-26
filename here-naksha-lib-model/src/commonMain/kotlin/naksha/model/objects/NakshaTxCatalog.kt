@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.AnyObject
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.Action
import naksha.base.Id
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmOverloads

/**
 * An information about what changes in this map.
 * @since 3.0
 */
@JsExport
class NakshaTxCatalog() : AnyObject() {

    @JvmOverloads
    @JsName("of")
    constructor(id: Id, action: Action? = null) : this() {
        this.id = id.text
        this.number = id.intValue
        this.action = action
    }

    companion object NakshaTxMap_C {
        private val ID = NotNullProperty<NakshaTxCatalog, String>(String::class)
        private val NUMBER = NotNullProperty<NakshaTxCatalog, Int>(Int::class) { self, _ ->
            // If the number misses in the JSON, we can calculate it.
            Id.numberToInt(Id.textToNumber(self.id))
        }
        private val ACTION_OR_NULL = NullableProperty<NakshaTxCatalog, Action>(Action::class)
        private val COLLECTIONS = NotNullProperty<NakshaTxCatalog, NakshaTxCollectionById>(NakshaTxCollectionById::class)
    }

    /**
     * The textual identifier of this catalog.
     * @since 3.0
     */
    var id: String by ID

    /**
     * The numeric identifier of this catalog.
     * @since 3.0
     */
    var number: Int by NUMBER

    /**
     * The action done to this catalog, `null` if just children of the catalog where modified.
     * @since 3.0
     */
    var action: Action? by ACTION_OR_NULL

    /**
     * The collections of this catalog that have been modified as part of this transaction.
     * @since 3.0
     */
    var collections: NakshaTxCollectionById by COLLECTIONS

    /**
     * Returns the collection-info of the given collection, if it does not yet exist, creates a new empty one.
     * @param id the collection-id.
     * @param action the action being done to the collection, `null` if only children were modified.
     * @return the transaction collection information.
     * @since 3.0
     */
    @JvmOverloads
    fun useCollection(id: Id, action: Action? = null): NakshaTxCollection {
        var info = collections[id.text]
        if (info != null) {
            if (action != null) info.action = action
            return info
        }
        info = NakshaTxCollection(id, action)
        collections[id.text] = info
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
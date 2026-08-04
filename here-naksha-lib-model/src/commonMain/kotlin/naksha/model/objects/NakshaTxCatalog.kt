@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.PAnyMap
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.Action
import naksha.base.Id
import naksha.base.NotNullIdProperty
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmOverloads

/**
 * An information about what changes in this map.
 * @since 3.0
 */
@JsExport
class NakshaTxCatalog() : PAnyMap() {

    @JvmOverloads
    @JsName("of")
    constructor(id: Id, action: Action? = null) : this() {
        this.id = id
        this.action = action
    }

    companion object NakshaTxCatalog_C {
        private val ID_NOT_NULL = NotNullIdProperty<NakshaTxCatalog>(randomId = false)
        private val ACTION_OR_NULL = NullableProperty<NakshaTxCatalog, Action>(Action::class)
        private val COLLECTIONS = NotNullProperty<NakshaTxCatalog, NakshaTxCollectionByIdText>(NakshaTxCollectionByIdText::class)
    }

    /**
     * The catalog-id of the catalog for which this information is for.
     * @since 3.0
     */
    var id: Id by ID_NOT_NULL

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
     * @param id the `id` of the collection.
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
     * @param collectionInfo the collection info to add.
     * @since 3.0
     */
    fun addCollection(collectionInfo: NakshaTxCollection) {
        val existing = collections[collectionInfo.id.text]
        if (existing != null) existing.addValues(collectionInfo) else collections[collectionInfo.id.text] = collectionInfo
    }
}
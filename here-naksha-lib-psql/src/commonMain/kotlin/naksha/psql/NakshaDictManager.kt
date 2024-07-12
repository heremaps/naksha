package naksha.psql

import naksha.jbon.IDictManager
import naksha.jbon.JbDict
import kotlin.js.JsExport

/**
 * The Naksha dictionary manager that reads dictionary from a storage. It can either refer to the global dictionary table (`naksha_meta`)
 * or to a local dictionary table of a specific collection (`{collection}_meta`).
 * @property session The Naksha session to which the manager is bound.
 * @property collectionId The collection-id, if bound to a specific collection (empty when bound to the global dictionary collection).
 */
@JsExport
class NakshaDictManager(val session: PgSession, val collectionId: String = "") : IDictManager {

    // TODO: If this is not the global dictionary manager (collectionId==""), then query the global manager for ids prefixed with a colon!!

    override fun putDictionary(dict: JbDict) {
        TODO("Not yet implemented")
    }

    override fun deleteDictionary(dict: JbDict): Boolean {
        TODO("Not yet implemented")
    }

    override fun getDictionary(id: String): JbDict? {
        // TODO("Not yet implemented")
        return null
    }
}
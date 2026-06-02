@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.jbon.IDictManager
import naksha.jbon.JbDictionary
import naksha.model.Naksha
import naksha.model.objects.NakshaCollection
import kotlin.js.JsExport

/**
 * The internal collection in the admin-map, that keeps track of the books (global JBON2 dictionaries) of the storage.
 */
@JsExport
class PgNakshaBooks internal constructor(adminMap: PgAdminMap) : PgCollection(adminMap, NakshaCollection()
    .withMapId(Naksha.ADMIN_MAP)
    .withId(Naksha.BOOKS_COL)
), PgInternalCollection, IDictManager {

    override fun putDictionary(dict: JbDictionary) {
        TODO("Not yet implemented")
    }

    override fun deleteDictionary(dict: JbDictionary): Boolean {
        TODO("Not yet implemented")
    }

    override fun getDictionary(id: String): JbDictionary? {
        // TODO: Implement me!
        return null
    }
}

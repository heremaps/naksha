@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.jbon.IDictManager
import naksha.jbon.JbDictionary
import naksha.model.Naksha
import kotlin.js.JsExport

/**
 * The internal dictionaries table.
 */
@JsExport
class PgNakshaDictionaries internal constructor(schema: PgSchema) :
    PgCollection(schema, Naksha.VIRT_DICTIONARIES), PgInternalCollection, IDictManager
{

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

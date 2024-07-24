@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.jbon.IDictManager
import naksha.jbon.JbDictDecoder
import kotlin.js.JsExport

/**
 * The internal dictionaries table.
 */
@JsExport
class PgNakshaDictionaries internal constructor(schema: PgSchema) : PgCollection(schema, ID), PgInternalCollection, IDictManager {
    companion object NakshaDictionariesCompanion {
        const val ID = "naksha~dictionaries"
    }

    override fun putDictionary(dict: JbDictDecoder) {
        TODO("Not yet implemented")
    }

    override fun deleteDictionary(dict: JbDictDecoder): Boolean {
        TODO("Not yet implemented")
    }

    override fun getDictionary(id: String): JbDictDecoder? {
        // TODO: Implement me!
        return null
    }
}

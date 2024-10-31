package naksha.psql

import naksha.jbon.IDictManager
import naksha.jbon.JbDictionary

internal class PgDictManager internal constructor(val storage: PgStorage) : IDictManager {
    override fun putDictionary(dict: JbDictionary) {
        TODO("Not yet implemented")
    }

    override fun deleteDictionary(dict: JbDictionary): Boolean {
        TODO("Not yet implemented")
    }

    override fun getDictionary(id: String): JbDictionary? = null
}
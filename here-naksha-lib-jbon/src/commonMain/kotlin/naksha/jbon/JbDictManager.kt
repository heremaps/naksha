package naksha.jbon

import kotlin.js.JsExport

/**
 * A simple dictionary manager that only cache dictionaries in memory.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class JbDictManager : IDictManager {
    private val globalDictionaries = HashMap<String, JbDictDecoder>()

    override fun putDictionary(dict: JbDictDecoder) {
        val id = dict.id()
        check(id != null)
        globalDictionaries[id] = dict
    }

    override fun deleteDictionary(dict: JbDictDecoder) : Boolean {
        val id = dict.id()
        if (id != null && globalDictionaries[id] === dict) {
            globalDictionaries.remove(id)
            return true
        }
        return false
    }

    override fun getDictionary(id: String): JbDictDecoder? {
        return globalDictionaries[id]
    }
}
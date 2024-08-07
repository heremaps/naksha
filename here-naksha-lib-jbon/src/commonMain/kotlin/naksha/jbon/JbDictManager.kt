package naksha.jbon

import kotlin.js.JsExport

/**
 * A simple dictionary manager that only cache dictionaries in memory.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class JbDictManager : IDictManager {
    private val globalDictionaries = HashMap<String, JbDictionary>()

    override fun putDictionary(dict: JbDictionary) {
        val id = dict.id()
        check(id != null)
        globalDictionaries[id] = dict
    }

    override fun deleteDictionary(dict: JbDictionary) : Boolean {
        val id = dict.id()
        if (id != null && globalDictionaries[id] === dict) {
            globalDictionaries.remove(id)
            return true
        }
        return false
    }

    override fun getDictionary(id: String): JbDictionary? {
        return globalDictionaries[id]
    }
}
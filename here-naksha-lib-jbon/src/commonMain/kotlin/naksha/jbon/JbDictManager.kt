package naksha.jbon

import kotlin.js.JsExport

/**
 * A simple dictionary manager that only cache dictionaries in memory.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class JbDictManager : IDictManager {
    private val globalDictionaries = HashMap<String, JbDict>()

    override fun putDictionary(dict: JbDict) {
        val id = dict.id()
        check(id != null)
        globalDictionaries[id] = dict
    }

    override fun deleteDictionary(dict: JbDict) : Boolean {
        val id = dict.id()
        if (id != null && globalDictionaries[id] === dict) {
            globalDictionaries.remove(id)
            return true
        }
        return false
    }

    override fun getDictionary(id: String): JbDict? {
        return globalDictionaries[id]
    }
}
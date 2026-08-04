package naksha.jbon

import naksha.base.Base
import kotlin.js.JsExport

/**
 * A thread safe in-memory dictionary manager, that only keep dictionaries in memory.
 * @since 3.0.0
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class JbDictManager : IDictManager {
    private val cache = Base.newAtomicMap<String, JbDictionary>()

    override fun putDictionary(dict: JbDictionary) {
        val id = dict.id
        check(id != null)
        cache[id] = dict
    }

    override fun deleteDictionary(dict: JbDictionary) : Boolean {
        val id = dict.id
        if (id != null) return cache.remove(id, dict)
        return false
    }

    override fun getDictionary(id: String): JbDictionary? {
        return cache[id]
    }
}
package naksha.jbon

import naksha.base.Platform
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A thread safe in-memory dictionary manager, that only keep dictionaries in memory.
 * @since 3.0.0
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class JbDictManager : IDictManager {

    companion object JbDictManager_C {
        /**
         * The [PlatformType] of [JbDictManager].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(JbDictManager::class).withPackageName(PACKAGE_NAME)
    }

    private val cache = Platform.newAtomicMap<String, JbDictionary>()

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
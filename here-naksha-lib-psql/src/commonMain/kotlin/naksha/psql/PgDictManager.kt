@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import naksha.jbon.IDictManager
import naksha.jbon.JbDictionary
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

internal class PgDictManager internal constructor(val storage: PgStorage) : IDictManager {
    companion object PgDictManager_C {
        /**
         * The [PlatformType] of [PgDictManager].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(PgDictManager::class).withPackageName(PACKAGE_NAME)
    }

    override fun putDictionary(dict: JbDictionary) {
        TODO("Not yet implemented")
    }

    override fun deleteDictionary(dict: JbDictionary): Boolean {
        TODO("Not yet implemented")
    }

    override fun getDictionary(id: String): JbDictionary? = null
}
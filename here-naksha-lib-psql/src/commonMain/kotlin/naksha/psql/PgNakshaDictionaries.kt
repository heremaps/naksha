@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import naksha.jbon.IDictManager
import naksha.jbon.JbDictionary
import naksha.model.Naksha
import naksha.model.objects.NakshaCollection
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The internal collection in the admin-map, that keeps track of the dictionaries of the storage.
 */
@JsExport
class PgNakshaDictionaries internal constructor(adminMap: PgAdminMap) : PgCollection(adminMap, NakshaCollection()
    .withMapId(Naksha.ADMIN_MAP)
    .withId(Naksha.DICTIONARIES_COL)
), PgInternalCollection, IDictManager {

    companion object PgNakshaDictionaries_C {
        /**
         * The [PlatformType] of [PgNakshaDictionaries].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(PgNakshaDictionaries::class).withPackageName(PACKAGE_NAME)
    }

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

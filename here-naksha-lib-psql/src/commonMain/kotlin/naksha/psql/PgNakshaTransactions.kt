@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import naksha.model.Naksha.Naksha_C.ADMIN_MAP
import naksha.model.Naksha.Naksha_C.TRANSACTIONS_COL
import naksha.model.Naksha.Naksha_C.TRANSACTIONS_COL_NUMBER
import naksha.model.objects.NakshaCollection
import naksha.model.objects.StoreMode
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The internal collection in the admin-map, that keeps track of the transactions of the storage.
 */
@JsExport
class PgNakshaTransactions internal constructor(adminMap: PgAdminMap) : PgCollection(adminMap, NakshaCollection().apply {
    mapId = ADMIN_MAP
    id = TRANSACTIONS_COL
    storeDeleted = StoreMode.OFF
    storeHistory = StoreMode.OFF
    storeMeta = StoreMode.OFF
}), PgInternalCollection {

    companion object PgNakshaTransactions_C {
        /**
         * The [PlatformType] of [PgNakshaTransactions].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(PgNakshaTransactions::class).withPackageName(PACKAGE_NAME)
    }

    /**
     * The transactions table, which actually is just [headTable].
     */
    val transactions: PgTransactions
        get() = headTable as PgTransactions
}

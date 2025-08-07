@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import naksha.model.objects.NakshaCollection
import naksha.model.Naksha
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The internal collection in the admin-map, that keeps track of the maps of the storage.
 */
@JsExport
class PgNakshaMaps internal constructor(adminMap: PgAdminMap) : PgCollection(adminMap, NakshaCollection().apply {
    mapId = Naksha.ADMIN_MAP
    id = Naksha.MAPS_COL
}), PgInternalCollection {
    companion object PgNakshaMaps_C {
        /**
         * The [PlatformType] of [PgNakshaMaps].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(PgNakshaMaps::class).withPackageName(PACKAGE_NAME)
    }
}

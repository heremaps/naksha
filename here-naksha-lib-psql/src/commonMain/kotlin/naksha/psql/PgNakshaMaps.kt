@file:OptIn(ExperimentalJsExport::class)

package naksha.psql

import naksha.model.objects.NakshaCollection
import naksha.model.Naksha
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * The internal collection in the admin-map, that keeps track of the maps of the storage.
 */
@JsExport
class PgNakshaMaps internal constructor(adminMap: PgAdminMap) : PgCollection(adminMap, NakshaCollection()
    .withMapId(Naksha.ADMIN_MAP)
    .withId(Naksha.MAPS_COL)
    .withNumber(Naksha.MAPS_COL_NUMBER)
), PgInternalCollection

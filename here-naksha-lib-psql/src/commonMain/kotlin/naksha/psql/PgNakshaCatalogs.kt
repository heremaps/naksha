@file:OptIn(ExperimentalJsExport::class)

package naksha.psql

import naksha.model.objects.NakshaCollection
import naksha.model.Naksha
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * The internal collection in the admin-map, that keeps track of the catalogs (maps) of the storage.
 */
@JsExport
class PgNakshaCatalogs internal constructor(adminMap: PgAdminMap) : PgCollection(adminMap, NakshaCollection()
    .withMapId(Naksha.ADMIN_CATALOG_ID)
    .withId(Naksha.CATALOGS_COL_ID)
), PgInternalCollection

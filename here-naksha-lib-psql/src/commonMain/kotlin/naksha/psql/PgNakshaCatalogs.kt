@file:OptIn(ExperimentalJsExport::class)

package naksha.psql

import naksha.base.Id
import naksha.base.Id.Id_C.ADMIN_CATALOG_ID
import naksha.base.Id.Id_C.CATALOGS_COL_ID
import naksha.model.objects.NakshaCollection
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * The internal collection in the admin-map, that keeps track of the catalogs (maps) of the storage.
 */
@JsExport
class PgNakshaCatalogs internal constructor(adminCatalog: PgAdminCatalog) : PgCollection(adminCatalog, NakshaCollection()
    .withDatabaseId(adminCatalog.databaseId)
    .withCatalogId(ADMIN_CATALOG_ID)
    .withId(CATALOGS_COL_ID)
), PgInternalCollection

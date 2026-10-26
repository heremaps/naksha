@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.COLLECTIONS_COL_TEXT
import naksha.model.objects.NakshaCollection
import kotlin.js.JsExport

/**
 * The internal collection in each map that keeps track of the collections being in the map.
 */
@JsExport
class PgNakshaCollections internal constructor(catalog: PgCatalog) : PgCollection(catalog, NakshaCollection()
    .withCatalogId(catalog.id.text)
    .withId(COLLECTIONS_COL_TEXT)
    .withAdminMembers()
    .withAdminIndices()
), PgInternalCollection

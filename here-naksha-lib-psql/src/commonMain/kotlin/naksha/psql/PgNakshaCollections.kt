@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.Id
import naksha.model.Naksha
import naksha.model.objects.NakshaCollection
import kotlin.js.JsExport

/**
 * The internal collection in each map that keeps track of the collections being in the map.
 */
@JsExport
class PgNakshaCollections internal constructor(map: PgCatalog) : PgCollection(map, NakshaCollection()
    .withCatalogId(map.id)
    .withId(Id.COLLECTIONS_COL_ID)
), PgInternalCollection

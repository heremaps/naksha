@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.model.Naksha
import naksha.model.objects.NakshaCollection
import kotlin.js.JsExport

/**
 * The internal collection in each map that keeps track of the collections being in the map.
 */
@JsExport
class PgNakshaCollections internal constructor(map: PgMap) : PgCollection(map, NakshaCollection()
    .withMapId(map.id)
    .withId(Naksha.ADMIN_COL_ID)
), PgInternalCollection

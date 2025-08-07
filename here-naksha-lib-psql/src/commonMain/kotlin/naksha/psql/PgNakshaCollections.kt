@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import naksha.model.Naksha
import naksha.model.objects.NakshaCollection
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The internal collection in each map that keeps track of the collections being in the map.
 */
@JsExport
class PgNakshaCollections internal constructor(map: PgMap) : PgCollection(map, NakshaCollection().apply {
    mapId = map.id
    id = Naksha.COLLECTIONS_COL
}), PgInternalCollection {
    companion object PgNakshaCollections_C {
        /**
         * The [PlatformType] of [PgNakshaCollections].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(PgNakshaCollections::class).withPackageName(PACKAGE_NAME)
    }
}

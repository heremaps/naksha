@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.MapProxy
import kotlin.js.JsExport

/**
 * A map between the map-id and the details about what changed in this map within the transaction.
 * @since 3.0
 */
@JsExport
class NakshaTxCatalogByName : MapProxy<String, NakshaTxCatalog>(String::class, NakshaTxCatalog::class)

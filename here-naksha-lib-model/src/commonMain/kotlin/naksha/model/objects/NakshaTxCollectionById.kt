@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.MapProxy
import kotlin.js.JsExport

/**
 * A map between the collection identifier, and details information about what changed within the collection.
 * @since 3.0
 */
@JsExport
class NakshaTxCollectionById : MapProxy<String, NakshaTxCollection>(String::class, NakshaTxCollection::class)
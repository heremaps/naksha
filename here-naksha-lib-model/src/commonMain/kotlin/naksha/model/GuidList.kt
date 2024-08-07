@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.ListProxy
import kotlin.js.JsExport

/**
 * A list of [Guid]'s.
 *
 * **Warning**: A [Guid] is not serializable, and it is not possible to create it without parameters.
 */
@JsExport
class GuidList : ListProxy<Guid>(Guid::class)

@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.base.AbstractMapProxy
import kotlin.js.JsExport

@JsExport
class NakshaLockOwnersProxy : AbstractMapProxy<String, Int64>(String::class, Int64::class)

@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.P_Map
import kotlin.js.JsExport

@JsExport
class NakshaLockOwnersProxy : P_Map<String, Int64>(String::class, Int64::class)

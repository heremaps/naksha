@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport

@JsExport
open class AnyMapProxy : MapProxy<Any, Any>(Any::class, Any::class)
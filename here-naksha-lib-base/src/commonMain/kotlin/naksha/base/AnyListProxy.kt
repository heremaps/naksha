@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport

@JsExport
open class AnyListProxy : ListProxy<Any>(Any::class)

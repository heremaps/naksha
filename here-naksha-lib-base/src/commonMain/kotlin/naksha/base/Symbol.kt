@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Symbols represent private members of [PlatformObject]'s, which are invisible when being serialized. They differ from fields in Java in
 * that symbols can be created and removed at runtime. Proxies are added to native objects using symbols. A symbol is a primitive value
 * and managed by the platform code via the [Platform] singleton.
 */
@JsExport
@JsName("Symbol")
interface Symbol

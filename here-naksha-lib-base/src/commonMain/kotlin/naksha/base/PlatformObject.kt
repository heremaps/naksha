@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A platform object is any object that supports [SymbolMember], so runtime linked hidden members, plus management of arbitrary properties.
 *
 * ### Note
 * In _Java_ this is implemented in `JvmObject`, in _JavaScript_ this is simply `Object`. Therefore, in _JavaScript_ all objects can be used to link proxies, while in _Java_ only those extending `JvmObject` can be linked against [proxies][Proxy].
 * @see [Platform.isPlatformObject]
 */
@JsExport
@JsName("Object")
interface PlatformObject

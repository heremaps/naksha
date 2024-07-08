@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.reflect.KClass

/**
 * The platform object is any object that allows to apply proxies. All platform objects can store symbols, which will not
 * be serialized and are hidden properties of objects.
 */
@JsExport
@JsName("Object")
interface PlatformObject
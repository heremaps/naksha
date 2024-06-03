@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.reflect.KClass

/**
 * A not thread safe list, where values may be _null_, but not _undefined_.
 */
@JsExport
@JsName("Array")
interface PlatformList : PlatformObject {
}

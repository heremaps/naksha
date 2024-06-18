package naksha.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.reflect.KClass

/**
 * A not thread safe list, where values may be _null_, but not _undefined_.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("Array")
interface PlatformList : PlatformObject

package naksha.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.reflect.KClass

/**
 * A data-view that allows to read and mutate the bytes of a byte-array.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("DataView")
interface PlatformDataView : PlatformObject


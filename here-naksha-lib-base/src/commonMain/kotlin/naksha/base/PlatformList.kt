package naksha.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A not thread safe list, where values may be _null_.
 *
 * ### Note
 * In _Java_ this in implemented in `JvmList`, which extends `JvmObject` and therefore supports runtime [Proxy] linking. In _JavaScript_ this is a pure [Array](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array).
 * @since 3.0
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("Array")
interface PlatformList : PlatformObject


package naksha.base

import kotlin.js.JsExport

/**
 * An abstraction to represent an iterator result, basically an object entry as returned by a platform iterator.
 * @property done Set to _true_ if the iterator reached the end, [value] will be _undefined_.
 * @property value The value or _undefined_, if [done] is _true_.
 */
// https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Iteration_protocols#the_iterator_protocol
@Suppress("OPT_IN_USAGE")
@JsExport
open class PlatformIteratorResult<VALUE>(var done: Boolean, var value: VALUE?)

package naksha.base

import naksha.base.Platform.PlatformCompanion.forKClass
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * An abstraction to represent an iterator result, basically an object entry as returned by a platform iterator.
 * @property done Set to _true_ if the iterator reached the end, [value] will be _undefined_.
 * @property value The value or _undefined_, if [done] is _true_.
 */
// https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Iteration_protocols#the_iterator_protocol
@Suppress("OPT_IN_USAGE")
@JsExport
open class PlatformIteratorResult<VALUE>(var done: Boolean, var value: VALUE?) {
    companion object PlatformIteratorResultCompanion {
        /**
         * The [PlatformType] of [PlatformIteratorResult].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(PlatformIteratorResult::class).withPackageName(PACKAGE_NAME)
    }
}

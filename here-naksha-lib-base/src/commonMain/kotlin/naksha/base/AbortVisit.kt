@file:Suppress("OPT_IN_USAGE")

package naksha.base

import naksha.base.Platform.Platform_C.forKClass
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * A special exception value to be thrown using [NakshaException] to abort a visit, for example [AtomicSet.forEach].
 * @since 3.0
 * @property value The value to return.
 */
@JsExport
class AbortVisit<T>(val value: T) : NakshaError(ABORT_VISIT, "aborted visit") {
    companion object AbortVisit_C : NakshaError() {
        /**
         * The [PlatformType] of [AbortVisit].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(AbortVisit::class).withPackageName(PACKAGE_NAME)

        /**
         * Aborts a visitor with the given value, the method does not return, because it throws an exception.
         * @param value The value to return.
         * @since 3.0
         */
        @JvmStatic
        @JsStatic
        fun <T> with(value: T): Nothing = throw NakshaException(AbortVisit(value))

        init { initialize() }
    }
}
package naksha.base

import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A weak reference.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
@JsName("WeakRef")
interface WeakRef<T: Any> {
    // https://kotlinlang.org/docs/operator-overloading.html#infix-calls-for-named-functions

    /**
     * Returns the reference or _null_, if it was collected.
     */
    fun deref(): T?
}
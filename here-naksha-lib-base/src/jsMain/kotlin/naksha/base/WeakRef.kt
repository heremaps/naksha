package naksha.base

import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * The JavaScript weak reference.
 */
@JsExport
@JsName("WeakRef")
interface WeakRef<T: Any> {
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/WeakRef
    // https://kotlinlang.org/docs/operator-overloading.html#infix-calls-for-named-functions

    /**
     * Returns the reference or _null_, if it was collected.
     */
    fun deref(): T?
}
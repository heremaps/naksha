@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "OPT_IN_USAGE")

package naksha.base

import kotlin.math.min

/**
 * JS actual of [BaseArray]. In JavaScript this class truly extends the native `Array`,
 * so `jsArray instanceof Array` evaluates to `true`.
 *
 * The generated JavaScript is equivalent to:
 * ```javascript
 * class JsArray extends Array { constructor() { super(); } }
 * ```
 * @since 3.0
 */
@JsExport
actual class BaseArray actual constructor() : BaseObject() {
    @JsName("newBaseArray")
    actual constructor(vararg elements: Any?) {
        
    }

    @JsName("newBaseArrayWithCapacity")
    actual constructor(capacity: Int) : this()
    // JS arrays resize dynamically, so capacity is a no-op hint here.

    private var _array: dynamic = null

    /**
     * The underlying JavaScript `Array`.
     * @since 3.0
     */
    var array: dynamic
        get() {
            var array = _array
            if (array == null) {
                array = js("[]")
                _array = array
            }
            return array
        }
        set(value) {
            if (!js("value instanceof Array").unsafeCast<Boolean>()) {
                throw illegalArg("The 'value' must be a JavaScript Array instance")
            }
            _array = value
        }

    /**
     * Maps the given JavaScript `Array`, wrapping it.
     * @param array the JavaScript `Array` to back this wrapper.
     * @return this
     */
    fun withArray(array: dynamic): BaseArray {
        this.array = array
        return this
    }

    /**
     * The number of elements in the array. Setting this truncates or pads with _undefined_.
     * @since 3.0
     */
    actual var length: Int
        get() = array.length.unsafeCast<Int>()
        set(value) { array.length = value }

    /**
     * Pre-allocated capacity hint. JavaScript Arrays resize dynamically, so this is a no-op on JS.
     * Reading it returns the current [length].
     * @since 3.0
     */
    actual var capacity: Int
        get() = length
        set(@Suppress("UNUSED_PARAMETER") value) { /* no-op: JS arrays resize dynamically */ }

    /**
     * Returns the element at [position], or _null_ if out of bounds.
     * @since 3.0
     */
    actual fun get(position: Int): Any? {
        if (_array == null) return null
        return unbox(array[position].unsafeCast<Any?>())
    }

    /**
     * Sets the element at [index] to [value]. JS Arrays auto-extend so no manual padding needed.
     * @since 3.0
     */
    actual fun set(index: Int, value: Any?): Any? {
        val arr = array
        val old = unbox(arr[index].unsafeCast<Any?>())
        arr[index] = box(value)
        return old
    }

    /**
     * Appends [value] to the end of the array.
     * @since 3.0
     */
    actual fun push(value: Any?): Int = array.push(box(value)).unsafeCast<Int>()

    /**
     * Removes and returns the last element, or _null_ if empty.
     * @since 3.0
     */
    actual fun pop(): Any? {
        if (_array == null) return null
        return unbox(array.pop().unsafeCast<Any?>())
    }

    /**
     * Inserts [value] at the beginning of the array.
     * @since 3.0
     */
    actual fun unshift(value: Any?): Int = array.unshift(box(value)).unsafeCast<Int>()

    /**
     * Removes and returns the first element, or _null_ if empty.
     * @since 3.0
     */
    actual fun shift(): Any? {
        if (_array == null) return null
        return unbox(array.shift().unsafeCast<Any?>())
    }

    /**
     * Removes [deleteCount] elements at [start] and inserts [items].
     * @since 3.0
     */
    actual fun splice(start: Int, deleteCount: Int, vararg items: Any?): BaseArray {
        val arr = array
        val boxed = Array(items.size) { box(items[it]) }
        val removed = BaseArray()
        removed.array = js("eval('arr.splice(start, deleteCount, ...boxed)')")
        return removed
    }

    /**
     * Returns the first index of [element] starting from [fromIndex], or `-1`.
     * @since 3.0
     */
    actual fun indexOf(element: Any?, fromIndex: Int): Int {
        if (_array == null) return -1
        return array.indexOf(element, min(length, fromIndex)).unsafeCast<Int>()
    }

    /**
     * Returns the last index of [element] searching backwards from [fromIndex], or `-1`.
     * @since 3.0
     */
    actual fun lastIndexOf(element: Any?, fromIndex: Int): Int {
        if (_array == null) return -1
        return array.lastIndexOf(element, fromIndex).unsafeCast<Int>()
    }

    /**
     * Removes all elements by setting length to 0.
     * @since 3.0
     */
    actual fun clear() {
        if (_array != null) array.length = 0
    }
}

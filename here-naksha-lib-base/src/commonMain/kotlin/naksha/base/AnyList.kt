@file:Suppress("OPT_IN_USAGE")

package naksha.base

import naksha.base.Platform.PlatformCompanion.toJSON
import kotlin.js.JsExport

/**
 * Standard definition of a list that can hold any value.
 * - [AnyList]
 * - [AnyMap]
 * - [AnyObject]
 */
@JsExport
open class AnyList : ListProxy<Any>(Any::class) {
    /**
     * Adds the specified element to the end of this list.
     * @param element the element to add.
     * @return this.
     * @since 3.0
     */
    fun append(element: Any?): AnyList {
        super.add(element)
        return this
    }

    /**
     * Convert this list into an integer-array.
     * @param convertNonBoolean if _true_ all elements not being [Boolean] are converted into boolean; otherwise an exception is thrown.
     * @return this list as [BooleanArray].
     * @throws NakshaException with error [ILLEGAL_STATE][NakshaError.ILLEGAL_STATE] if `convertNonBoolean` is _false_ and an element is no boolean.
     * @since 3.0
     */
    fun toBooleanArray(convertNonBoolean: Boolean): BooleanArray {
        val array = BooleanArray(size)
        var end = 0
        for (i in 0 until size) {
            val item = get(i)
            if (item is Boolean) {
                array[end++] = item
            } else if (convertNonBoolean) {
                array[end++] = when (item) {
                    is String -> "true".equals(item, ignoreCase = true)
                    is Float -> item != 0.0f
                    is Double -> item != 0.0
                    is Number -> item.toLong() != 0L
                    else -> false
                }
            } else throw illegalState("The element at index $i is no boolean")
        }
        return if (end == array.size) array else array.copyOf(end)
    }

    /**
     * Convert this list into an short-array.
     * @param ignoreIllegalValues if _true_ invalid elements, `null` elements, or elements not being in the valid short range are ignored; otherwise an exception is thrown.
     * @return this list as [ShortArray].
     * @throws NakshaException with error [ILLEGAL_STATE][NakshaError.ILLEGAL_STATE] if `ignoreIllegalValues` is _false_ and an element is no 16-bit integer, this includes integers being out of the valid 16-bit range _(so being too small or large)_.
     * @since 3.0
     */
    fun toShortArray(ignoreIllegalValues: Boolean): ShortArray {
        val array = ShortArray(size)
        var end = 0
        for (i in 0 until size) {
            val item = get(i)
            if (item is Number) {
                val value = item.toLong()
                if (value in Short.MIN_VALUE.toLong() ..  Short.MAX_VALUE.toLong()) {
                    array[end++] = value.toShort()
                    continue
                }
            }
            if (!ignoreIllegalValues) {
                throw illegalState("The element at index $i is no valid short")
            }
        }
        return if (end == array.size) array else array.copyOf(end)
    }

    /**
     * Convert this list into an integer-array.
     * @param ignoreIllegalValues if _true_ invalid elements, `null` elements, or elements not being in the valid integer range are ignored; otherwise an exception is thrown.
     * @return this list as [IntArray].
     * @throws NakshaException with error [ILLEGAL_STATE][NakshaError.ILLEGAL_STATE] if `ignoreIllegalValues` is _false_ and an element is no 32-bit integer, this includes integers being out of the valid 32-bit range _(so being too small or large)_.
     * @since 3.0
     */
    fun toIntArray(ignoreIllegalValues: Boolean): IntArray {
        val array = IntArray(size)
        var end = 0
        for (i in 0 until size) {
            val item = get(i)
            if (item is Number) {
                val value = item.toLong()
                if (value in Int.MIN_VALUE.toLong() ..  Int.MAX_VALUE.toLong()) {
                    array[end++] = value.toInt()
                    continue
                }
            }
            if (!ignoreIllegalValues) {
                throw illegalState("The element at index $i is no valid integer")
            }
        }
        return if (end == array.size) array else array.copyOf(end)
    }

    /**
     * Convert this list into an integer-array.
     * @param ignoreIllegalValues if _true_ invalid elements, `null` elements, or elements not being in the valid integer range are ignored; otherwise an exception is thrown.
     * @return this list as [LongArray].
     * @throws NakshaException with error [ILLEGAL_STATE][NakshaError.ILLEGAL_STATE] if `ignoreIllegalValues` is _false_ and an elements is no 64-bit integer.
     * @since 3.0
     */
    fun toLongArray(ignoreIllegalValues: Boolean): LongArray {
        val array = LongArray(size)
        var end = 0
        for (i in 0 until size) {
            val item = get(i)
            if (item is Number) {
                array[end++] = item.toLong()
                continue
            }
            if (!ignoreIllegalValues) {
                throw illegalState("The element at index $i is no valid long")
            }
        }
        return if (end == array.size) array else array.copyOf(end)
    }

    /**
     * Convert this list into a float-array.
     * @param ignoreIllegalValues if _true_ invalid elements, `null` elements are ignored; otherwise an exception is thrown.
     * @return this list as [DoubleArray].
     * @throws NakshaException with error [ILLEGAL_STATE][NakshaError.ILLEGAL_STATE] if `ignoreIllegalValues` is _false_ and an elements is no number.
     * @since 3.0
     */
    fun toFloatArray(ignoreIllegalValues: Boolean): FloatArray {
        val array = FloatArray(size)
        var end = 0
        for (i in 0 until size) {
            val item = get(i)
            if (item is Number) {
                array[end++] = item.toFloat()
                continue
            }
            if (!ignoreIllegalValues) {
                throw illegalState("The element at index $i is no valid number")
            }
        }
        return if (end == array.size) array else array.copyOf(end)
    }

    /**
     * Convert this list into a double-array.
     * @param ignoreIllegalValues if _true_ invalid elements, `null` elements are ignored; otherwise an exception is thrown.
     * @return this list as [DoubleArray].
     * @throws NakshaException with error [ILLEGAL_STATE][NakshaError.ILLEGAL_STATE] if `ignoreIllegalValues` is _false_ and an elements is no number.
     * @since 3.0
     */
    fun toDoubleArray(ignoreIllegalValues: Boolean): DoubleArray {
        val array = DoubleArray(size)
        var end = 0
        for (i in 0 until size) {
            val item = get(i)
            if (item is Number) {
                array[end++] = item.toDouble()
                continue
            }
            if (!ignoreIllegalValues) {
                throw illegalState("The element at index $i is no valid number")
            }
        }
        return if (end == array.size) array else array.copyOf(end)
    }

    /**
     * Convert this list into an array of [ByteArray].
     * @param ignoreIllegalValues if _true_ elements not being a [ByteArray] are ignored; otherwise an exception is thrown.
     * @return this list as array of strings.
     * @throws NakshaException with error [ILLEGAL_STATE][NakshaError.ILLEGAL_STATE] if `ignoreIllegalValues` is _false_ and an element is no [ByteArray].
     * @since 3.0
     */
    fun toByteArrayArray(ignoreIllegalValues: Boolean): Array<ByteArray> {
        val array = arrayOfNulls<ByteArray>(size)
        var end = 0
        for (i in 0 until size) {
            val item = get(i)
            if (item is ByteArray) {
                array[end++] = item
                continue
            }
            if (!ignoreIllegalValues) {
                throw illegalState("The element at index $i is no valid string")
            }
        }
        @Suppress("UNCHECKED_CAST")
        return (if (end == array.size) array else array.copyOf(end)) as Array<ByteArray>
    }

    /**
     * Convert this list into a string-array.
     * @param ignoreIllegalValues if _true_ elements not being string are ignored; otherwise an exception is thrown.
     * @return this list as array of strings.
     * @throws NakshaException with error [ILLEGAL_STATE][NakshaError.ILLEGAL_STATE] if `ignoreIllegalValues` is _false_ and an element is no string.
     * @since 3.0
     */
    fun toStringArray(ignoreIllegalValues: Boolean): Array<String> {
        val array = arrayOfNulls<String>(size)
        var end = 0
        for (i in 0 until size) {
            val item = get(i)
            if (item is String) {
                array[end++] = item
                continue
            }
            if (!ignoreIllegalValues) {
                throw illegalState("The element at index $i is no valid string")
            }
        }
        @Suppress("UNCHECKED_CAST")
        return (if (end == array.size) array else array.copyOf(end)) as Array<String>
    }

    /**
     * Convert this list into a string-array.
     * @param invalidToNull if _true_ invalid elements are turned into `null` values.
     * @param ignoreIllegalValues if `invalidToNull` is _false_ and this argument is _true_, invalid elements are ignored _(be removed from the returned list)_; otherwise an exception is thrown for invalid elements.
     * @return this list as array of strings, may contain `null` values.
     * @throws NakshaException with error [ILLEGAL_STATE][NakshaError.ILLEGAL_STATE] if `ignoreIllegalValues` is _false_ and an element is neither `null` nor a string.
     * @since 3.0
     */
    fun toStringNullableArray(invalidToNull: Boolean, ignoreIllegalValues: Boolean): Array<String?> {
        val array = arrayOfNulls<String>(size)
        var end = 0
        for (i in 0 until size) {
            var item = get(i)
            if (item != null && item !is String) {
                if (invalidToNull) item = null
                else if (ignoreIllegalValues) continue
                else throw illegalState("The element at index $i is no valid string")
            }
            array[end++] = item
        }
        return if (end == array.size) array else array.copyOf(end)
    }

    /**
     * Convert this list into a string-array that contains all elements `JSON` serialized.
     * @return this list as a string-array of `JSON` serialized elements.
     * @since 3.0
     */
    fun toJsonArray(): Array<String> = Array(size) { toJSON(it) }

}

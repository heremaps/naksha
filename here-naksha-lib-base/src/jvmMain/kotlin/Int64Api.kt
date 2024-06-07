@file:Suppress("SortModifiers", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.here.naksha.lib.base

actual internal class Int64Api {
    actual companion object {
        @Suppress("NOTHING_TO_INLINE")
        private inline fun l(lo: Any?): Long = if (lo is Number) lo.toLong() else throw IllegalArgumentException("Number expected")

        @Suppress("NOTHING_TO_INLINE")
        private inline fun l(i: Int): Long = i.toLong()

        @JvmStatic
        actual fun unaryPlus(self: Int64): Int64 = self

        @JvmStatic
        actual fun unaryMinus(self: Int64): Int64 = JvmInt64(-l(self))

        @JvmStatic
        actual fun inc(self: Int64): Int64 = JvmInt64(l(self) + 1)

        @JvmStatic
        actual fun dec(self: Int64): Int64 = JvmInt64(l(self) - 1)

        @JvmStatic
        actual fun plus(self: Int64, other: Any): Int64 = JvmInt64(l(self) + l(other))

        @JvmStatic
        actual fun minus(self: Int64, other: Any): Int64 = JvmInt64(l(self) - l(other))

        @JvmStatic
        actual fun times(self: Int64, other: Any): Int64 = JvmInt64(l(self) * l(other))

        @JvmStatic
        actual fun div(self: Int64, other: Any): Int64 = JvmInt64(l(self) / l(other))

        @JvmStatic
        actual fun rem(self: Int64, other: Any): Int64 = JvmInt64(l(self) % l(other))

        @JvmStatic
        actual fun compareTo(self: Int64, other: Any?): Int = l(self).compareTo(l(other))

        @JvmStatic
        actual fun equals(self: Int64, other: Any?): Boolean = l(self) == l(other)

        @JvmStatic
        actual fun shr(self: Int64, bits: Int): Int64 = JvmInt64(l(self) shr bits)

        @JvmStatic
        actual fun ushr(self: Int64, bits: Int): Int64 = JvmInt64(l(self) ushr bits)

        @JvmStatic
        actual fun shl(self: Int64, bits: Int): Int64 = JvmInt64(l(self) shl bits)

        @JvmStatic
        actual fun and(self: Int64, other: Int64): Int64 = JvmInt64(l(self) and l(other))

        @JvmStatic
        actual fun or(self: Int64, other: Int64): Int64 = JvmInt64(l(self) or l(other))

        @JvmStatic
        actual fun xor(self: Int64, other: Int64): Int64 = JvmInt64(l(self) xor l(other))

        @JvmStatic
        actual fun inv(self: Int64): Int64 = JvmInt64(l(self).inv())
    }
}
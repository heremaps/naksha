@file:Suppress("SortModifiers", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.here.naksha.lib.base

import com.here.naksha.lib.base.Platform.Companion.longToInt64

actual internal class Int64Api {
    actual companion object {
        @Suppress("NOTHING_TO_INLINE")
        private inline fun l(lo: Int64): Long = if (lo is JvmInt64) lo.toLong() else 0L

        @Suppress("NOTHING_TO_INLINE")
        private inline fun l(i: Int): Long = i.toLong()

        @JvmStatic
        actual fun eq(t: Int64, o: Int64): Boolean = l(t) == l(o)

        @JvmStatic
        actual fun eqi(t: Int64, o: Int): Boolean = l(t) == l(o)

        @JvmStatic
        actual fun lt(t: Int64, o: Int64): Boolean = l(t) < l(o)

        @JvmStatic
        actual fun lti(t: Int64, o: Int): Boolean = l(t) < l(o)

        @JvmStatic
        actual fun lte(t: Int64, o: Int64): Boolean = l(t) <= l(o)

        @JvmStatic
        actual fun ltei(t: Int64, o: Int): Boolean = l(t) <= l(o)

        @JvmStatic
        actual fun gt(t: Int64, o: Int64): Boolean = l(t) > l(o)

        @JvmStatic
        actual fun gti(t: Int64, o: Int): Boolean = l(t) > l(o)

        @JvmStatic
        actual fun gte(t: Int64, o: Int64): Boolean = l(t) >= l(o)

        @JvmStatic
        actual fun gtei(t: Int64, o: Int): Boolean = l(t) >= l(o)

        @JvmStatic
        actual fun shr(t: Int64, bits: Int): Int64 = longToInt64(l(t) shr bits)

        @JvmStatic
        actual fun ushr(t: Int64, bits: Int): Int64 = longToInt64(l(t) ushr bits)

        @JvmStatic
        actual fun shl(t: Int64, bits: Int): Int64 = longToInt64(l(t) shl bits)

        @JvmStatic
        actual fun add(t: Int64, o: Int64): Int64 = longToInt64(l(t) + l(o))

        @JvmStatic
        actual fun addi(t: Int64, o: Int): Int64 = longToInt64(l(t) + l(o))

        @JvmStatic
        actual fun sub(t: Int64, o: Int64): Int64 = longToInt64(l(t) - l(o))

        @JvmStatic
        actual fun subi(t: Int64, o: Int): Int64 = longToInt64(l(t) - l(o))

        @JvmStatic
        actual fun mul(t: Int64, o: Int64): Int64 = longToInt64(l(t) * l(o))

        @JvmStatic
        actual fun muli(t: Int64, o: Int): Int64 = longToInt64(l(t) * l(o))

        @JvmStatic
        actual fun mod(t: Int64, o: Int64): Int64 = longToInt64(l(t) % l(o))

        @JvmStatic
        actual fun modi(t: Int64, o: Int): Int64 = longToInt64(l(t) % l(o))

        @JvmStatic
        actual fun div(t: Int64, o: Int64): Int64 = longToInt64(l(t) / l(o))

        @JvmStatic
        actual fun divi(t: Int64, o: Int): Int64 = longToInt64(l(t) / l(o))

        @JvmStatic
        actual fun and(t: Int64, o: Int64): Int64 = longToInt64(l(t) and l(o))

        @JvmStatic
        actual fun or(t: Int64, o: Int64): Int64 = longToInt64(l(t) or l(o))

        @JvmStatic
        actual fun xor(t: Int64, o: Int64): Int64 = longToInt64(l(t) xor l(o))

        @JvmStatic
        actual fun inv(t: Int64): Int64 = longToInt64(l(t).inv())
    }
}
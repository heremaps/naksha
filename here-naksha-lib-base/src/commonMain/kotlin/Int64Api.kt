package com.here.naksha.lib.base

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
internal expect class Int64Api {
    companion object {
        fun unaryPlus(self: Int64): Int64
        fun unaryMinus(self: Int64): Int64

        fun inc(self: Int64): Int64
        fun dec(self: Int64): Int64

        fun plus(self: Int64, other: Any): Int64
        fun minus(self: Int64, other: Any): Int64
        fun times(self: Int64, other: Any): Int64
        fun div(self: Int64, other: Any): Int64
        fun rem(self: Int64, other: Any): Int64

        fun compareTo(self: Int64, other: Any?): Int
        fun equals(self: Int64, other: Any?): Boolean

        fun shr(self: Int64, bits: Int): Int64
        fun ushr(self: Int64, bits: Int): Int64
        fun shl(self: Int64, bits: Int): Int64
        fun and(self: Int64, other: Int64): Int64
        fun or(self: Int64, other: Int64): Int64
        fun xor(self: Int64, other: Int64): Int64
        fun inv(self: Int64): Int64
    }
}

package com.here.naksha.lib.base

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
internal expect class Int64Api {
    companion object {

        fun eq(t: Int64, o: Int64): Boolean
        fun eqi(t: Int64, o: Int): Boolean
        fun lt(t: Int64, o: Int64): Boolean
        fun lti(t: Int64, o: Int): Boolean
        fun lte(t: Int64, o: Int64): Boolean
        fun ltei(t: Int64, o: Int): Boolean
        fun gt(t: Int64, o: Int64): Boolean
        fun gti(t: Int64, o: Int): Boolean
        fun gte(t: Int64, o: Int64): Boolean
        fun gtei(t: Int64, o: Int): Boolean
        fun shr(t: Int64, bits: Int): Int64
        fun ushr(t: Int64, bits: Int): Int64
        fun shl(t: Int64, bits: Int): Int64
        fun add(t: Int64, o: Int64): Int64
        fun addi(t: Int64, o: Int): Int64
        fun sub(t: Int64, o: Int64): Int64
        fun subi(t: Int64, o: Int): Int64
        fun mul(t: Int64, o: Int64): Int64
        fun muli(t: Int64, o: Int): Int64
        fun mod(t: Int64, o: Int64): Int64
        fun modi(t: Int64, o: Int): Int64
        fun div(t: Int64, o: Int64): Int64
        fun divi(t: Int64, o: Int): Int64
        fun and(t: Int64, o: Int64): Int64
        fun or(t: Int64, o: Int64): Int64
        fun xor(t: Int64, o: Int64): Int64
        fun inv(t: Int64): Int64
    }
}

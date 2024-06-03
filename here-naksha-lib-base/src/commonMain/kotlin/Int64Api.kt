@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(ExperimentalJsStatic::class)

package com.here.naksha.lib.base

import kotlin.js.ExperimentalJsStatic
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

internal expect class Int64Api {
    companion object {
        @JvmStatic
        @JsStatic
        fun eq(t: Int64, o: Int64): Boolean

        @JvmStatic
        @JsStatic
        fun eqi(t: Int64, o: Int): Boolean

        @JvmStatic
        @JsStatic
        fun lt(t: Int64, o: Int64): Boolean

        @JvmStatic
        @JsStatic
        fun lti(t: Int64, o: Int): Boolean

        @JvmStatic
        @JsStatic
        fun lte(t: Int64, o: Int64): Boolean

        @JvmStatic
        @JsStatic
        fun ltei(t: Int64, o: Int): Boolean

        @JvmStatic
        @JsStatic
        fun gt(t: Int64, o: Int64): Boolean

        @JvmStatic
        @JsStatic
        fun gti(t: Int64, o: Int): Boolean

        @JvmStatic
        @JsStatic
        fun gte(t: Int64, o: Int64): Boolean

        @JvmStatic
        @JsStatic
        fun gtei(t: Int64, o: Int): Boolean

        @JvmStatic
        @JsStatic
        fun shr(t: Int64, bits: Int): Int64

        @JvmStatic
        @JsStatic
        fun ushr(t: Int64, bits: Int): Int64

        @JvmStatic
        @JsStatic
        fun shl(t: Int64, bits: Int): Int64

        @JvmStatic
        @JsStatic
        fun add(t: Int64, o: Int64): Int64

        @JvmStatic
        @JsStatic
        fun addi(t: Int64, o: Int): Int64

        @JvmStatic
        @JsStatic
        fun sub(t: Int64, o: Int64): Int64

        @JvmStatic
        @JsStatic
        fun subi(t: Int64, o: Int): Int64

        @JvmStatic
        @JsStatic
        fun mul(t: Int64, o: Int64): Int64

        @JvmStatic
        @JsStatic
        fun muli(t: Int64, o: Int): Int64

        @JvmStatic
        @JsStatic
        fun mod(t: Int64, o: Int64): Int64

        @JvmStatic
        @JsStatic
        fun modi(t: Int64, o: Int): Int64

        @JvmStatic
        @JsStatic
        fun div(t: Int64, o: Int64): Int64

        @JvmStatic
        @JsStatic
        fun divi(t: Int64, o: Int): Int64

        @JvmStatic
        @JsStatic
        fun and(t: Int64, o: Int64): Int64

        @JvmStatic
        @JsStatic
        fun or(t: Int64, o: Int64): Int64

        @JvmStatic
        @JsStatic
        fun xor(t: Int64, o: Int64): Int64

        @JvmStatic
        @JsStatic
        fun inv(t: Int64): Int64
    }
}

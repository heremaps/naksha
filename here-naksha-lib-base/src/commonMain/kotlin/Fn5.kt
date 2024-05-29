@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport

@JsExport
interface Fn5<R, A, B, C, D, E> : Fn {
    fun call(a: A, b: B, c: C, d: D, e: E): R
}
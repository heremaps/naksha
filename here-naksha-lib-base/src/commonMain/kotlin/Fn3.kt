@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport

@JsExport
interface Fn3<R, A, B, C> : Fn {
    fun call(a: A, b: B, c: C): R
}
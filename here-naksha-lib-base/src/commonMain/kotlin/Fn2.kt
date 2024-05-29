@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport

@JsExport
interface Fn2<R, A, B> : Fn {
    fun call(a: A, b: B): R
}
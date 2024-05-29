@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport

@JsExport
interface Fn0<R> : Fn {
    fun call(): R
}
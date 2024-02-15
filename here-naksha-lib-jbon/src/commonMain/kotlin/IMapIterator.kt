@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
interface IMapIterator {
    fun hasNext() : Boolean

    fun next(): Boolean

    fun key(): String

    fun value(): Any?
}
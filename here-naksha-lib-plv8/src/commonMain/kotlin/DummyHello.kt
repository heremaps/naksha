package com.here.naksha.lib.plv8

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class DummyHello {

    fun sayHello() = "hello world"

    fun add(a: Int, b: Int) = a + b
}
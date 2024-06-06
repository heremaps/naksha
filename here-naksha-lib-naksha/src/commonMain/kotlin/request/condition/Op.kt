package com.here.naksha.lib.naksha.request

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
interface Op {

    fun getType(): OpType
}
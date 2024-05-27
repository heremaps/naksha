package com.here.naksha.lib.base.response

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class Response(
    val type: String
) {
    abstract fun size(): Int

    companion object {
        const val ERROR_TYPE = "ERROR"
        const val SUCCESS_TYPE = "SUCCESS"
    }
}
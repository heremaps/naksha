package com.here.naksha.lib.plv8

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
interface PLV8Session {

    fun elog(level:String, msg:String, vararg args:String)

    fun version() : String
}
package com.here.naksha.lib.base

import com.here.naksha.lib.base.request.ResultRow
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
interface IReadRowFilter {

    fun filterRow(row: ResultRow): ResultRow
}
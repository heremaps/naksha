package com.here.naksha.lib.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class ReadRow(
        val op: String,
        val id: String? = null,
        val uuid: String? = null,
        val type: String? = null,
        val row: Row?, // optional - for retained purged rows
        private val feature: NakFeature? = null
)

package com.here.naksha.lib.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@OptIn(ExperimentalJsExport::class)
@JsExport
class NakReadRow(
        val op: String,
        val id: String? = null,
        val uuid: String? = null,
        var feature: NakFeature? = null,
        val type: String? = null,
        val xyz: NakXyz? = null,
        var row: NakRow? = null
)

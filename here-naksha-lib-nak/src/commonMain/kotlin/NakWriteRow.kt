package com.here.naksha.lib.base

import com.here.naksha.lib.nak.Flags
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
data class NakWriteRow(
        val op: Int,
        val id: String? = null,
        val uuid: String? = null,
        var feature: NakFeature? = null,
        var row: NakRow? = null,
        val flags: Flags = Flags(),
        val grid: Int? = null
)
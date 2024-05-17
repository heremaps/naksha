package com.here.naksha.lib.base

import com.here.naksha.lib.nak.Flags
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class AbstractWrite(
        var op: Int,
        var id: String? = null,
        var uuid: String? = null,
        var flags: Flags = Flags(),
        var grid: Int? = null
)
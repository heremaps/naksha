package com.here.naksha.lib.base

import com.here.naksha.lib.nak.Flags
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

@OptIn(ExperimentalJsExport::class)
@JsExport
class WriteRow(
        op: Int,
        id: String? = null,
        uuid: String? = null,
        flags: Flags = Flags(),
        grid: Int? = null,
        val row: NakRow? = null
) : AbstractWrite(op, id, uuid, flags, grid) {
    @JsName("WriteRowAction")
    constructor(op: Int, row: NakRow) : this(op = op, id = null, row = row)
}
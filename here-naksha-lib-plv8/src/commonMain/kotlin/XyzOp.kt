@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * The operation to be executed.
 */
@JsExport
class XyzOp : XyzSpecial<XyzOp>() {
    private var op: Int = 0
    private var id: String? = null
    private var uuid: String? = null
    private var crid: String? = null

    override fun parseHeader(mandatory: Boolean) {
        super.parseHeader(mandatory)
        check(variant == XYZ_OP)

        op = reader.readInt32()
        check(reader.nextUnit())
        id = if (reader.isString()) reader.readString() else null
        check(reader.nextUnit())
        uuid = if (reader.isString()) reader.readString() else null
        check(reader.nextUnit())
        crid = if (reader.isString()) reader.readString() else null

        noContent()
    }

    fun op(): Int = op
    fun id(): String? = id
    fun uuid(): String? = uuid
    fun crid(): String? = crid
}
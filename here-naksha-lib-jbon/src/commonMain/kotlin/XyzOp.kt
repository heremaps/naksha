@file:OptIn(ExperimentalJsExport::class)
package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * The operation to be executed.
 */
@JsExport
class XyzOp : XyzSpecial<XyzOp>() {
    private var op: Int = 0
    private var id: String? = null
    private var uuid: String? = null

    companion object {
        @JvmStatic
        fun getOpName(op:Int) = if (op >= 0 && op <= XYZ_OP_NAME.size) XYZ_OP_NAME[op] else "undefined"

        @JvmStatic
        fun getOpCode(op:String) : Int {
            val names = XYZ_OP_NAME
            var i = 0
            while (i < names.size) {
                val name = names[i]
                if (name.equals(op, true)) return XYZ_OP_INT[i]
                i++
            }
            return -1
        }
    }

    override fun parseHeader(mandatory: Boolean) {
        super.parseHeader(mandatory)
        check(variant == XYZ_OP)

        op = reader.readInt32()
        check(reader.nextUnit())
        id = if (reader.isString()) reader.readString() else null
        check(reader.nextUnit())
        uuid = if (reader.isString()) reader.readString() else null

        noContent()
    }

    fun op(): Int = op
    fun id(): String? = id
    fun uuid(): String? = uuid
    fun toIMap() : IMap {
        val map = Jb.map.newMap()
        map["op"] = getOpName(op)
        map["id"] = id
        map["uuid"] = uuid
        return map
    }
}
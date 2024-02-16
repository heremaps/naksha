@file:OptIn(ExperimentalJsExport::class)
package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
class XyzBuilder(view: IDataView, global: JbDict? = null) : JbBuilder(view, global) {

    /**
     * Starts tag building.
     */
    fun startTags() {
        reset()
        val view = this.view
        view.setInt8(end++, TYPE_XYZ.toByte())
        writeInt32(XYZ_TAGS)
        val global = this.global
        if (global == null) writeNull() else writeString(global.id()!!)
    }

    /**
     * Write a tag, splits the tag at the first equal sign, optionally uses a value split (`:=`).
     * @param tag The tag to write.
     */
    fun writeTag(tag: String) {
        val global = this.global
        val key: String
        val value: Any?
        val i = tag.indexOf('=')
        if (i <= 0 || i + 1 == tag.length) {
            key = tag
            value = null
        } else {
            if (tag[i - 1] != ':') {
                key = tag.substring(0, i)
                value = tag.substring(i + 1)
            } else {
                key = tag.substring(0, i - 1)
                val raw = tag.substring(i + 1)
                value = if ("true".equals(raw, true)) {
                    true
                } else if ("false".equals(raw, true)) {
                    false
                } else {
                    raw.toDoubleOrNull()
                }
            }
        }
        var index = global?.indexOf(key) ?: -1
        if (index >= 0) writeRef(index, true) else writeString(key)
        when (value) {
            is Boolean -> writeBool(value)

            is Double -> {
                if (Jb.env.canBeInt32(value)) {
                    writeInt32(value.toInt())
                } else if (Jb.env.canBeFloat32(value)) {
                    writeFloat32(value.toFloat())
                } else {
                    writeFloat64(value)
                }
            }

            is String -> {
                index = global?.indexOf(value) ?: -1
                if (index >= 0) writeRef(index, true) else writeString(value)
            }

            else -> writeNull()
        }
    }

    /**
     * Finish the tag building and returns the build tag bytes.
     * @return The tag bytes.
     */
    fun buildTags() : ByteArray {
        return view.getByteArray().copyOf(end)
    }

    /**
     * Build an JBON encoded XYZ operation. Clears the builder before operating.
     * @param op The operation, being [CREATE](XYZ_OP_CREATE), [UPSERT](XYZ_OP_UPSERT), [UPDATE](XYZ_OP_UPDATE),
     *           [DELETE](XYZ_OP_DELETE) or [PURGE](XYZ_OP_PURGE).
     * @param id The **id** of the feature for which the operation is for. This is only necessary for
     *           [DELETE](XYZ_OP_DELETE) or [PURGE](XYZ_OP_PURGE).
     * @param uuid The state in which the feature must exist for the operation to perform. This is for atomic
     *             [UPDATE](XYZ_OP_UPDATE), [DELETE](XYZ_OP_DELETE) or [PURGE](XYZ_OP_PURGE). Has no effect for
     *             [CREATE](XYZ_OP_CREATE) and only impacts the **update** part of the [UPSERT](XYZ_OP_UPSERT).
     * @param crid The customer-reference-id to be set, if any.
     * @return The JBON encoded XYZ operation.
     */
    fun buildXyzOp(op: Int, id: String?, uuid: String?, crid: String?): ByteArray {
        reset()
        val view = this.view
        view.setInt8(end++, TYPE_XYZ.toByte())
        writeInt32(XYZ_OP)
        writeInt32(op)
        if (id == null) writeNull() else writeString(id)
        if (uuid == null) writeNull() else writeString(uuid)
        if (crid == null) writeNull() else writeString(crid)
        return view.getByteArray().copyOf(end)
    }

    /**
     * Creates an XYZ namespace from the given parameter. Clears the builder before operating.
     * @return The JBON encoded XYZ namespace.
     */
    fun buildXyzNs(
            createdAt: BigInt64,
            updatedAt: BigInt64,
            txn: BigInt64,
            action: Int?,
            version: Int,
            authorTs: BigInt64,
            extend: BigInt64,
            puuid: String?,
            uuid: String?,
            appId: String?,
            author: String?,
            crid: String?,
            grid: String?
    ): ByteArray {
        reset()
        val view = this.view
        view.setInt8(end++, TYPE_XYZ.toByte())
        writeInt32(XYZ_NS)
        writeTimestamp(createdAt)
        if (createdAt == updatedAt) writeNull() else writeTimestamp(updatedAt)
        writeInt64(txn)
        if (action == null) writeNull() else writeInt32(action)
        writeInt32(version)
        writeTimestamp(authorTs)
        writeInt64(extend)
        if (puuid == null) writeNull() else writeString(puuid)
        if (uuid == null) writeNull() else writeString(uuid)
        if (appId == null) writeNull() else writeString(appId)
        if (author == null) writeNull() else writeString(author)
        if (crid == null) writeNull() else writeString(crid)
        if (grid == null) writeNull() else writeString(grid)
        return view.getByteArray().copyOf(end)
    }

}
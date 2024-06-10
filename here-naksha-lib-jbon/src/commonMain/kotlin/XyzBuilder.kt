@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import naksha.base.Int64
import naksha.base.P_DataView
import naksha.base.Platform
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@JsExport
class XyzBuilder(view: P_DataView? = null, global: JbDict? = null) : JbBuilder(view, global) {

    companion object {
        /**
         * Create a new builder with a buffer of the given size.
         * @param size The buffer size to use.
         * @param global The global dictionary to use for the builder; if any.
         * @return The builder.
         */
        @JvmStatic
        fun create(size: Int? = null, global: JbDict? = null): XyzBuilder =
                XyzBuilder(P_DataView(), global)
    }

    /**
     * Starts tag building.
     */
    fun startTags() {
        end = 10
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
                if (Platform.canBeInt32(value)) {
                    writeInt(value.toInt())
                } else if (Platform.canBeFloat32(value)) {
                    writeFloat(value.toFloat())
                } else {
                    writeDouble(value)
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
     * Writes the structure header and in-front and then returns the copy of the content.
     * @param structType The structure type.
     * @param variant The variant; _null_ if no variant.
     * @return The bytes of the structure.
     */
    private fun finish(structType:Int, variant: Int): ByteArray {
        val startOfPayload = 10
        val endOfPayload = end
        val sizeOfPayload = endOfPayload - startOfPayload
        val startOfStruct = 10 - sizeOfStructHeader(sizeOfPayload, variant)
        end = startOfStruct
        writeStructHeader(structType, variant, sizeOfPayload)
        end = endOfPayload
        return view().getByteArray().copyOfRange(startOfStruct, end)
    }

    /**
     * Finish the tag building and returns the build tag bytes.
     * @return The tag bytes.
     */
    fun buildTags(): ByteArray {
        return finish(ENC_STRUCT_VARIANT_XYZ, XYZ_TAGS_VARIANT)
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
     * @param grid The geo reference id.
     * @return The JBON encoded XYZ operation.
     */
    fun buildXyzOp(op: Int, id: String? = null, uuid: String? = null, grid: Int? = null): ByteArray {
        end = 10
        writeInt(op)
        if (id == null) writeNull() else writeString(id)
        if (uuid == null) writeNull() else writeString(uuid)
        if (grid == null) writeNull() else writeInt(grid)
        return finish(ENC_STRUCT_VARIANT_XYZ, XYZ_OPS_VARIANT)
    }

    /**
     * Creates an XYZ namespace from the given parameter. Clears the builder before operating.
     * @return The JBON encoded XYZ namespace.
     */
    fun buildXyzNs(
        createdAt: Int64,
        updatedAt: Int64,
        txn: Int64,
        action: Short,
        version: Int,
        authorTs: Int64,
        puuid: String?,
        uuid: String,
        appId: String,
        author: String,
        grid: Int
    ): ByteArray {
        end = 10
        writeTimestamp(createdAt)
        if (createdAt == updatedAt) writeNull() else writeTimestamp(updatedAt)
        writeInt64(txn)
        writeInt(action.toInt())
        writeInt(version)
        if (authorTs == updatedAt) writeNull() else writeTimestamp(authorTs)
        if (puuid == null) writeNull() else writeString(puuid)
        writeString(uuid)
        writeString(appId)
        writeString(author)
        writeInt(grid)
        return finish(ENC_STRUCT_VARIANT_XYZ, XYZ_NS_VARIANT)
    }

}
@file:OptIn(ExperimentalJsExport::class)

package naksha.jbon

import naksha.base.Binary
import naksha.base.BinaryView
import naksha.base.Int64
import naksha.base.Platform
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A builder for XYZ structures.
 * @constructor Creates a new empty, mutable, and resizable binary editor.
 */
@JsExport
class XyzEncoder(binaryView: BinaryView = Binary(), global: JbDict? = null) : JbEncoder(binaryView = binaryView, global = global) {

    /**
     * Starts tag building.
     */
    fun startTags() {
        end = 10
        val global = this.global
        if (global == null) encodeNull() else encodeString(global.id()!!)
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
        if (index >= 0) encodeRef(index, true) else encodeString(key)
        when (value) {
            is Boolean -> encodeBool(value)

            is Double -> {
                if (Platform.canBeInt32(value)) {
                    encodeInt(value.toInt())
                } else if (Platform.canBeFloat32(value)) {
                    encodeFloat(value.toFloat())
                } else {
                    encodeDouble(value)
                }
            }

            is String -> {
                index = global?.indexOf(value) ?: -1
                if (index >= 0) encodeRef(index, true) else encodeString(value)
            }

            else -> encodeNull()
        }
    }

    /**
     * Writes the structure header and in-front and then returns the copy of the content.
     * @param structType The structure type.
     * @param variant The variant; _null_ if no variant.
     * @return The bytes of the structure.
     */
    private fun finish(structType: Int, variant: Int): ByteArray {
        val startOfPayload = 10
        val endOfPayload = end
        val sizeOfPayload = endOfPayload - startOfPayload
        val startOfStruct = 10 - sizeOfStructHeader(sizeOfPayload, variant)
        end = startOfStruct
        writeStructHeader(structType, variant, sizeOfPayload)
        end = endOfPayload
        return byteArray.copyOfRange(startOfStruct, end)
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
        encodeInt(op)
        if (id == null) encodeNull() else encodeString(id)
        if (uuid == null) encodeNull() else encodeString(uuid)
        if (grid == null) encodeNull() else encodeInt(grid)
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
        encodeTimestamp(createdAt)
        if (createdAt == updatedAt) encodeNull() else encodeTimestamp(updatedAt)
        writeInt64(txn)
        encodeInt(action.toInt())
        encodeInt(version)
        if (authorTs == updatedAt) encodeNull() else encodeTimestamp(authorTs)
        if (puuid == null) encodeNull() else encodeString(puuid)
        encodeString(uuid)
        encodeString(appId)
        encodeString(author)
        encodeInt(grid)
        return finish(ENC_STRUCT_VARIANT_XYZ, XYZ_NS_VARIANT)
    }

}
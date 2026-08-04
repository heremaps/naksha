@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.base.PlatformDataView
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_get_int16
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_get_int32
import naksha.base.getInt16Be
import naksha.base.getInt32Be
import naksha.base.setInt32Be
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * A helper to encode and decode a binary header.
 * @since 3.0.0
 */
@JsExport
class BinaryUtil private constructor() {
    companion object BinaryUtil_C {
        /**
         * Naksha-binary-type value for [TupleNumberBinaryArray].
         * @since 3.0.0
         */
        const val TYPE_TUPLE_NUMBER_ARRAY = 0

        /**
         * Naksha-binary-type value for [MetadataBinaryObject].
         * @since 3.0.0
         */
        const val TYPE_METADATA_OBJECT = 1

        /**
         * Naksha-binary-type value for [MetadataBinaryArray].
         * @since 3.0.0
         */
        const val TYPE_METADATA_ARRAY = 2

        /**
         * Naksha-binary-type value for [TupleBinaryObject].
         * @since 3.0.0
         */
        const val TYPE_TUPLE_OBJECT = 3

        /**
         * Naksha-binary-type value for [TupleBinaryArray].
         * @since 3.0.0
         */
        const val TYPE_TUPLE_ARRAY = 4

        /**
         * Write or update a header without any extensions _(therefore simple)_.
         *
         * This method does not perform any checks upon the given values, it will simply encode what was given, taking the risk that the result is invalid.
         * @param bytes the binary.
         * @param offset the byte-offset where the binary starts.
         * @param type the type to write, should be a value of [TYPE_TUPLE_NUMBER_ARRAY], [TYPE_METADATA_OBJECT], [TYPE_METADATA_ARRAY], [TYPE_TUPLE_OBJECT], or [TYPE_TUPLE_ARRAY].
         * @param subtype the subtype to write, depends on type (see e.g. [naksha.base.TupleNumberVariant.subType]).
         * @param length the length (number of entities).
         * @param size the size including the header (which is 8-byte), the client knows this, because it needs to allocate the buffer.
         * @return the offset where to start writing the content.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun writeSimpleHeader(bytes: ByteArray, offset: Int, type: Int, subtype: Int, length: Int, size: Int): Int {
            val len = ((type and 7) shl 28) or ((subtype and 15) shl 24) or (length and 0x00ff_ffff)
            bytes.setInt32Be(offset, len)
            bytes.setInt32Be(offset + 4, size)
            return offset + 8
        }

        /**
         * Tests if the header does have an extension section.
         * @param bytes the binary.
         * @param offset the byte-offset where the binary starts.
         * @return _true_ if this the header does have an extension section.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        @JvmOverloads
        fun hasExtension(bytes: ByteArray, offset: Int = 0): Boolean = bytes.getInt32Be(offset) shr 31 == 1

        /**
         * Reads the content-type. Possible values are:
         * - [TYPE_TUPLE_NUMBER_ARRAY]
         * - [TYPE_METADATA_OBJECT]
         * - [TYPE_METADATA_ARRAY]
         * - [TYPE_TUPLE_OBJECT]
         * - [TYPE_TUPLE_ARRAY]
         * @param bytes the binary.
         * @param offset the byte-offset where the binary starts.
         * @return the content-type.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        @JvmOverloads
        fun getType(bytes: ByteArray, offset: Int = 0): Int = (bytes.getInt32Be(offset) shr 28) and 7

        /**
         * Reads the content-subtype.
         *
         * @param bytes the binary.
         * @param offset the byte-offset where the binary starts.
         * @return the content-subtype.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        @JvmOverloads
        fun getSubType(bytes: ByteArray, offset: Int = 0): Int = (bytes.getInt32Be(offset) shr 24) and 15

        /**
         * Reads the length from the header.
         *
         * @param bytes the binary.
         * @param offset the byte-offset where the binary starts.
         * @return the length.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        @JvmOverloads
        fun getLength(bytes: ByteArray, offset: Int = 0): Int = bytes.getInt32Be(offset) and 16777215

        /**
         * Reads the byte-size from the header.
         *
         * @param bytes the binary.
         * @param offset the byte-offset where the binary starts.
         * @return the byte-size of the binary, including the header.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun getSize(bytes: ByteArray, offset: Int = 0): Int = bytes.getInt32Be(offset + 4)

        /**
         * Calculates the content-offset, skipping over the head and extension segment.
         *
         * @param bytes the binary.
         * @param offset the byte-offset in the binary starts.
         * @return the byte-offset of the content.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun getContentOffset(bytes: ByteArray, offset: Int = 0): Int
            = if (hasExtension(bytes, offset)) 8 + (bytes.getInt32Be(offset + 8) and 16777215) else 8

        /**
         * Helper to read a 48-bit unsigned integer (6-byte) used to store timestamps as 64-bit integer.
         * @param bytes the binary.
         * @param offset the byte-offset in the binary to read.
         * @return the 48-bit unsigned integer as 64-bit integer.
         */
        @JvmStatic
        @JsStatic
        fun readTimestamp(bytes: ByteArray, offset: Int): Int64
            = (Int64(bytes.getInt16Be(offset).toInt() and 0xffff) shl 16) or
              (Int64(bytes.getInt32Be(offset + 2)) and Int64(0xffff_ffffL))
    }
}
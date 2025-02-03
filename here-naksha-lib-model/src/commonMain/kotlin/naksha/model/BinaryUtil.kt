@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.base.PlatformDataView
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_get_int16
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_get_int32
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_get_int64
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_set_int32
import kotlin.js.JsExport
import kotlin.js.JsStatic
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
         * The subtype _(of the Tuple-Number-Array)_ to signal that all tuple-numbers are full encoded (224-bit, 28-byte, encoding).
         * @since 3.0.0
         */
        const val SUBTYPE_TNA_28_BYTE = 0

        /**
         * The subtype _(of the Tuple-Number-Array)_ to signal that the storage-number is shared and stored in the header _(20-byte/160-bit tuple-number encoding)_.
         * @since 3.0.0
         */
        const val SUBTYPE_TNA_20_BYTE = 1

        /**
         * The subtype _(of the Tuple-Number-Array)_ to signal that the storage-, and map-number are shared, and stored in the header _(16-byte/128-bit tuple-number encoding)_.
         * @since 3.0.0
         */
        const val SUBTYPE_TNA_16_BYTE = 2

        /**
         * The subtype _(of the Tuple-Number-Array)_ to signal that the storage-, map-, and collection-number are shared, and stored in the header _(12-byte/96-bit tuple-number encoding)_.
         * @since 3.0.0
         */
        const val SUBTYPE_TNA_12_BYTE = 3

        /**
         * Write or update a header without any extensions.
         *
         * This method does not perform any checks upon the given values, it will simply encode what was given, taking the risk that the result is invalid.
         * @param view the view into the binary.
         * @param offset the byte-offset in the view where the binary starts.
         * @param type the type to write, should be a value of [TYPE_TUPLE_NUMBER_ARRAY], [TYPE_METADATA_OBJECT], [TYPE_METADATA_ARRAY], [TYPE_TUPLE_OBJECT], or [TYPE_TUPLE_ARRAY].
         * @param subtype the subtype to write, depends on type.
         * @param length the length (number of entities).
         * @param size the size including the header (which is 8-byte), the client knows this, because it needs to allocate the buffer.
         * @return the offset where to start writing the content.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun writeSimpleHeader(view: PlatformDataView, offset: Int, type: Int, subtype: Int, length: Int, size: Int): Int {
            val len = ((type and 7) shl 28) or ((subtype and 15) shl 24) or (length and 0x00ff_ffff)
            dataview_set_int32(view, offset, len)
            dataview_set_int32(view, offset + 4, size)
            return offset + 8
        }

        /**
         * Tests if the header does have an extension section.
         * @param view the view into the binary.
         * @param offset the byte-offset in the view where the binary starts.
         * @return _true_ if this the header does have an extension section.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun hasExtension(view: PlatformDataView, offset: Int = 0): Boolean = dataview_get_int32(view, offset) shr 31 == 1

        /**
         * Reads the content-type. Possible values are:
         * - [TYPE_TUPLE_NUMBER_ARRAY]
         * - [TYPE_METADATA_OBJECT]
         * - [TYPE_METADATA_ARRAY]
         * - [TYPE_TUPLE_OBJECT]
         * - [TYPE_TUPLE_ARRAY]
         * @param view the view into the binary.
         * @param offset the byte-offset in the view where the binary starts.
         * @return the content-type.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun getType(view: PlatformDataView, offset: Int = 0): Int = (dataview_get_int32(view, offset) shr 28) and 7

        /**
         * Reads the content-subtype.
         *
         * @param view the view into the binary.
         * @param offset the byte-offset in the view where the binary starts.
         * @return the content-subtype.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun getSubType(view: PlatformDataView, offset: Int = 0): Int = (dataview_get_int32(view, offset) shr 24) and 15

        /**
         * Reads the length from the header.
         *
         * @param view the view into the binary.
         * @param offset the byte-offset in the view where the binary starts.
         * @return the length.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun getLength(view: PlatformDataView, offset: Int = 0): Int = dataview_get_int32(view, offset) and 16777215

        /**
         * Reads the byte-size from the header.
         *
         * @param view the view into the binary.
         * @param offset the byte-offset in the view where the binary starts.
         * @return the byte-size of the binary, including the header.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun getSize(view: PlatformDataView, offset: Int = 0): Int = dataview_get_int32(view, offset + 4)

        /**
         * Calculates the content-offset, skipping over the extension segment.
         *
         * @param view the view into the binary.
         * @param offset the byte-offset in the view where the binary starts.
         * @return the byte-offset of the content.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun getContentOffset(view: PlatformDataView, offset: Int = 0): Int
            = if (hasExtension(view, offset)) 8 + (dataview_get_int32(view, offset + 8) and 16777215) else 8

        /**
         * Helper to read a 48-bit unsigned integer (6-byte) used to store timestamps as 64-bit integer.
         * @param view the view into the binary.
         * @param offset the byte-offset in the view to read.
         * @return the 48-bit unsigned integer as 64-bit integer.
         */
        @JvmStatic
        @JsStatic
        fun readTimestamp(view: PlatformDataView, offset: Int): Int64
            =   (Int64(dataview_get_int16(view, offset).toInt() and 0xffff) shl 16) or
                (Int64(dataview_get_int32(view, offset + 2)) and Int64(0xffff_ffffL))

        /**
         * Read a binary encoded [TupleNumber]; can be used to fetch four encodings:
         * - 96-bit (12-byte), when storage-, map-, and collection-number given.
         * - 128-bit (16-byte), when storage- and map-number given.
         * - 160-bit (20-byte), when storage-number given.
         * - 224-bit (28-byte), otherwise.
         * @param view the view to read.
         * @param offset the byte-offset in the view.
         * @param storageNumber if the storage-number is not encoded.
         * @param mapNumber if the map-number is not encoded.
         * @param collectionNumber if the collection-number is not encoded.
         * @return the decoded [TupleNumber]; _null_ if [offset] is negative.
         */
        @JvmStatic
        @JsStatic
        fun readTupleNumber(
            view: PlatformDataView,
            offset: Int,
            storageNumber: Int64? = null,
            mapNumber: Int? = null,
            collectionNumber: Int? = null
        ): TupleNumber? {
            var pos = if (offset >= 0) offset else return null
            val sn: Int64
            if (storageNumber == null) {
                sn = dataview_get_int64(view, pos)
                pos += 8
            } else sn = storageNumber
            val mn: Int
            if (storageNumber == null || mapNumber == null) {
                mn = dataview_get_int32(view, pos)
                pos += 4
            } else mn = mapNumber
            val cn: Int
            if (storageNumber == null || mapNumber == null || collectionNumber == null) {
                cn = dataview_get_int32(view, pos)
                pos += 4
            } else cn = collectionNumber
            val raw = dataview_get_int64(view, pos)
            val pn = raw.toInt() and 0xff
            val txn = (raw shr 8) and Int64(0x00ff_ffff_ffff_ffffL)
            val uid = dataview_get_int32(view, pos + 8)
            return TupleNumber(sn, mn, cn, pn, Version(txn), uid)
        }
    }
}
@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.base.PlatformDataView
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_get_int16
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_get_int32
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_get_int64
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_set_int32
import naksha.base.TupleNumber
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
         * @param view the view into the binary.
         * @param offset the byte-offset in the view where the binary starts.
         * @param type the type to write, should be a value of [TYPE_TUPLE_NUMBER_ARRAY], [TYPE_METADATA_OBJECT], [TYPE_METADATA_ARRAY], [TYPE_TUPLE_OBJECT], or [TYPE_TUPLE_ARRAY].
         * @param subtype the subtype to write, depends on type (see e.g. [naksha.base.TupleNumberVariant.subType]).
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
         * Calculates the content-offset, skipping over the head and extension segment.
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
         * Read a binary encoded [naksha.base.TupleNumber]; can be used to fetch four encodings:
         * - 96-bit (12-byte), when storage-, map-, collection, and feature-number given.
         * - 160-bit (20-byte), when storage-, map-, and collection-number given.
         * - 192-bit (24-byte), when storage- and map-number given.
         * - 224-bit (28-byte), when storage-number given.
         * - 288-bit (36-byte), when nothing is given.
         * @param view the view to read.
         * @param offset the byte-offset in the view.
         * @param storageNumber if the storage-number is not encoded.
         * @param mapNumber if the map-number is not encoded.
         * @param collectionNumber if the collection-number is not encoded.
         * @param featureNumber if the feature-number is not encoded.
         * @return the decoded [naksha.base.TupleNumber]; _null_ if [offset] is negative.
         */
        @JsStatic
        @JvmStatic
        @JvmOverloads
        fun readTupleNumber(
            view: PlatformDataView,
            offset: Int,
            storageNumber: Int64? = null,
            mapNumber: Int? = null,
            collectionNumber: Int? = null,
            featureNumber: Int64? = null
        ): TupleNumber? {
            var pos = if (offset >= 0) offset else return null
            val sn: Int64 // storage-number
            if (storageNumber == null) {
                sn = dataview_get_int64(view, pos)
                pos += 8
            } else sn = storageNumber

            val mn: Int // map-number
            if (mapNumber == null) {
                mn = dataview_get_int32(view, pos)
                pos += 4
            } else mn = mapNumber

            val cn: Int // collection-number
            if (collectionNumber == null) {
                cn = dataview_get_int32(view, pos)
                pos += 4
            } else cn = collectionNumber

            val fn: Int64 // feature-number
            if (featureNumber == null) {
                fn = dataview_get_int64(view, pos)
                pos += 8
            } else fn = featureNumber
            val txn = dataview_get_int64(view, pos)
            return TupleNumber(sn, mn, cn, fn, txn)
        }
    }
}
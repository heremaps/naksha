@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.base.Platform
import naksha.base.PlatformDataView
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_get_int32
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_get_int64
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.request.FeatureTupleList
import naksha.model.request.FeatureTupleList.FeatureTupleList_C.fromByteArray
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * A helper that allows reading a binary encoded array of [TupleNumber]'s.
 *
 * @since 3.0.0
 */
@JsExport
data class TupleNumberBinaryArray(
    /**
     * The binary, uncompressed byte-array.
     * @see [fromGzip]
     * @since 3.0.0
     */
    @JvmField val bytes: ByteArray,

    /**
     * The offset where to start reading the header.
     * @since 3.0.0
     */
    @JvmField val headerOffset: Int = 0
) {
    private val view: PlatformDataView = Platform.newDataView(bytes, headerOffset)
    private val length: Int = BinaryUtil.getLength(view)
    private val contentOffset: Int
    private val sharedStorageNumber: Int64?
    private val sharedMapNumber: Int?
    private val sharedCollectionNumber: Int?
    private val entrySize: Int
    private val dataOffset: Int
    private val storagePos: Int
    private val mapPos: Int
    private val colPos: Int
    private val txnPos: Int
    private val uidPos: Int
    init {
        when (val subtype = BinaryUtil.getSubType(view)) {
            0 -> {
                contentOffset = BinaryUtil.getContentOffset(view)
                storagePos = 0
                mapPos = 8
                colPos = 12
                txnPos = 16
                uidPos = 24
                entrySize = 28
                sharedStorageNumber = null
                sharedMapNumber = null
                sharedCollectionNumber = null
                dataOffset = contentOffset
            }
            1 -> {
                contentOffset = BinaryUtil.getContentOffset(view) + 8
                storagePos = -1
                mapPos = 0
                colPos = 4
                txnPos = 8
                uidPos = 16
                entrySize = 20
                sharedStorageNumber = dataview_get_int64(view, contentOffset)
                sharedMapNumber = null
                sharedCollectionNumber = null
                dataOffset = contentOffset + 8
            }
            2 -> {
                contentOffset = BinaryUtil.getContentOffset(view) + 12
                storagePos = -1
                mapPos = -1
                colPos = 0
                txnPos = 4
                uidPos = 12
                entrySize = 16
                sharedStorageNumber = dataview_get_int64(view, contentOffset)
                sharedMapNumber = dataview_get_int32(view, contentOffset + 8)
                sharedCollectionNumber = null
                dataOffset = contentOffset + 12
            }
            3 -> {
                contentOffset = BinaryUtil.getContentOffset(view) + 12
                storagePos = -1
                mapPos = -1
                colPos = -1
                txnPos = 0
                uidPos = 8
                entrySize = 12
                sharedStorageNumber = dataview_get_int64(view, contentOffset)
                sharedMapNumber = dataview_get_int32(view, contentOffset + 8)
                sharedCollectionNumber = dataview_get_int32(view, contentOffset + 12)
                dataOffset = contentOffset + 16
            }
            else -> throw NakshaException(ILLEGAL_ARGUMENT, "The header of the binary stores subtype $subtype, which is invalid")
        }
    }
    private val last: Int = if (length <= 0 || bytes.size < 36) 0 else dataOffset + (length-1) * entrySize

    /**
     * The amount of [tuple-numbers][TupleNumber] in the array.
     * @since 3.0.0
     */
    val size: Int
        get() = length

    @Suppress("NOTHING_TO_INLINE")
    private inline fun offset(index: Int): Int = dataOffset + index * entrySize

    /**
     * Returns the tuple-number from the given index.
     * @param index the index.
     * @return the tuple-number or _null_, if out of bounds.
     * @since 3.0.0
     */
    operator fun get(index: Int): TupleNumber? {
        val offset = offset(index)
        if (offset < 0 || offset > last) return null
        val storageNumber = sharedStorageNumber ?: dataview_get_int64(view, offset + storagePos)
        val mapNumber = sharedMapNumber ?: dataview_get_int32(view, offset + mapPos)
        val colNumber = sharedCollectionNumber ?: dataview_get_int32(view, offset + colPos)
        val raw = dataview_get_int64(view, offset + txnPos)
        val txn = raw shr 8
        val partitionNumber = raw.toInt() and 255
        val version = Version(txn)
        val uid = dataview_get_int32(view, offset + uidPos)
        return TupleNumber(storageNumber, mapNumber, colNumber, partitionNumber, version, uid)
    }

    /**
     * Returns the storage-number from the given index.
     * @param index the index.
     * @return the storage-number.
     * @since 3.0.0
     */
    fun getStorageNumber(index: Int): Int64 {
        val offset = offset(index)
        if (offset < 0 || offset > last) throw IndexOutOfBoundsException()
        return sharedStorageNumber ?: dataview_get_int64(view, offset + storagePos)
    }

    /**
     * Returns the map-number from the given index.
     * @param index the index.
     * @return the map-number.
     * @since 3.0.0
     */
    fun getMapNumber(index: Int): Int {
        val offset = offset(index)
        if (offset < 0 || offset > last) throw IndexOutOfBoundsException()
        return sharedMapNumber ?: dataview_get_int32(view, offset + mapPos)
    }

    /**
     * Returns the collection-number from the given index.
     * @param index the index.
     * @return the collection-number.
     * @since 3.0.0
     */
    fun getCollectionNumber(index: Int): Int {
        val offset = offset(index)
        if (offset < 0 || offset > last) throw IndexOutOfBoundsException()
        return sharedMapNumber ?: dataview_get_int32(view, offset + colPos)
    }

    /**
     * Returns the partition-number from the given index.
     * @param index the index.
     * @return the partition-number (`0..255`).
     * @since 3.0.0
     */
    fun getPartitionNumber(index: Int): Int {
        val offset = offset(index)
        if (offset < 0 || offset > last) throw IndexOutOfBoundsException()
        return dataview_get_int64(view, offset + txnPos).toInt() and 255
    }

    /**
     * Returns the transaction-number from the given index.
     * @param index the index.
     * @return the transaction-number.
     * @since 3.0.0
     */
    fun getTxn(index: Int): Int64 {
        val offset = offset(index)
        if (offset < 0 || offset > last) throw IndexOutOfBoundsException()
        return dataview_get_int64(view, offset + txnPos) shr 8
    }

    /**
     * Returns the uid at the given index.
     * @param index the index.
     * @return the uid.
     * @since 3.0.0
     */
    fun getUid(index: Int): Int {
        val offset = offset(index)
        if (offset < 0 || offset > last) throw IndexOutOfBoundsException()
        return dataview_get_int32(view, offset + uidPos)
    }

    /**
     * Compress this byte-array and return the compressed version (this is helpful for caching).
     * @return the compressed tuple-number array.
     * @since 3.0.0
     */
    fun gzip(): ByteArray = Platform.gzipDeflate(bytes)

    /**
     * Unpack this into an array of [tuple-numbers][TupleNumber].
     * @return an array of [tuple-numbers][TupleNumber].
     * @since 3.0.0
     */
    fun toArray(): Array<TupleNumber> = Array(bytes.size) { get(it)!! }

    /**
     * Calculate the MD5 hash above the [bytes].
     * @return the MD5 hash above the [bytes].
     * @since 3.0
     */
    fun md5(): ByteArray = Platform.md5(bytes)

    /**
     * Helper method to convert this binary into a [FeatureTupleList].
     *
     * @param from the index of the first entry to convert.
     * @param to the index of the first entry **not** to convert.
     * @return the [FeatureTupleList].
     * @since 3.0
     */
    @JvmOverloads
    fun toFeatureTupleList(from:Int=0, to:Int=size): FeatureTupleList = fromByteArray(this, from, to)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as TupleNumberBinaryArray
        return bytes.contentEquals(other.bytes)
    }
    override fun hashCode(): Int = bytes.contentHashCode()
    override fun toString(): String = bytes.contentToString()

    companion object TupleNumberByteArray_C {
        /**
         * Return a [TupleNumberBinaryArray] from a compressed byte-array.
         * @param compressed the compressed tuple-number array.
         * @return the [TupleNumberBinaryArray].
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun fromGzip(compressed: ByteArray): TupleNumberBinaryArray = TupleNumberBinaryArray(Platform.gzipInflate(compressed))
    }
}
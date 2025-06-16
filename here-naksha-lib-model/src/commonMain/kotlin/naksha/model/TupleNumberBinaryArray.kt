@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.*
import naksha.base.PlatformDataViewApi.PlatformDataViewApi_C.dataview_get_byte_array
import naksha.base.PlatformDataViewApi.PlatformDataViewApi_C.dataview_get_int32
import naksha.base.PlatformDataViewApi.PlatformDataViewApi_C.dataview_get_int64
import naksha.base.NakshaError.NakshaError_C.ILLEGAL_ARGUMENT
import naksha.base.Platform.Platform_C.forKClass
import naksha.model.request.FeatureTupleList
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * A helper that allows reading a binary encoded array of [TupleNumber]'s.
 *
 * This binary can be serialized technically, but just using the [Platform.toJSON][naksha.base.Platform.toJson] method, because it requires a binary encoding, which means serialization and deserialization into a [Data URL](https://developer.mozilla.org/en-US/docs/Web/URI/Reference/Schemes/data), which is normally not supported out of the box by standard JSON parsers/serializers, it is a proprietary extension to the JSON standard, the same way that 64-bit integers are handled as special [Data URL](https://developer.mozilla.org/en-US/docs/Web/URI/Reference/Schemes/data) by [Platform.toJSON][naksha.base.Platform.toJson].
 *
 * @since 3.0
 */
@JsExport
data class TupleNumberBinaryArray(
    /**
     * The binary, uncompressed byte-array.
     * @see [fromGzip]
     * @see [fromByteArray]
     * @since 3.0
     */
    @JvmField val view: PlatformDataView
) : List<TupleNumber?> {
    /**
     * Create from mapping the given byte-array into a view.
     * @param bytes the byte-array to map.
     * @since 3.0
     */
    @JsName("ofByteArray")
    constructor(bytes: ByteArray) : this(Platform.newDataView(bytes))

    /**
     * Create from mapping the given byte-array into a view.
     * @param bytes the byte-array to map.
     * @param from the index of the first byte to map.
     * @param to the index of the first byte **not** to map _(end, exclusive)_.
     * @since 3.0
     */
    @JsName("fromByteArray")
    constructor(bytes: ByteArray, from:Int = 0, to:Int = bytes.size) : this(Platform.newDataView(bytes, from, to - from))

    /**
     * The underlying byte-array.
     * @since 3.0
     */
    val bytes: ByteArray
        get() = dataview_get_byte_array(view)

    private val length: Int = BinaryUtil.getLength(view)
    private val contentOffset: Int
    private val sharedStorageNumber: Int64?
    private val sharedMapNumber: Int?
    private val sharedCollectionNumber: Int?
    private val sharedFeatureNumber: Int64?
    private val entrySize: Int
    private val dataOffset: Int
    private val storageNumberOffset: Int
    private val mapNumberOffset: Int
    private val collectionNumberOffset: Int
    private val featureNumberOffset: Int
    private val txnOffset: Int
    private val uidOffset: Int
    init {
        when (val subtype = BinaryUtil.getSubType(view)) {
            0 -> { // 288-bit (storage-number, map-number, collection-number, feature-number, version, uid)
                contentOffset = BinaryUtil.getContentOffset(view)
                storageNumberOffset = 0
                mapNumberOffset = 8
                collectionNumberOffset = 12
                featureNumberOffset = 16
                txnOffset = 24
                uidOffset = 32
                entrySize = 36
                sharedStorageNumber = null
                sharedMapNumber = null
                sharedCollectionNumber = null
                sharedFeatureNumber = null
                dataOffset = contentOffset
            }
            1 -> { // 224-bit (map-number, collection-number, feature-number, version, uid)
                contentOffset = BinaryUtil.getContentOffset(view)
                storageNumberOffset = -1
                mapNumberOffset = 0
                collectionNumberOffset = 4
                featureNumberOffset = 12
                txnOffset = 20
                uidOffset = 24
                entrySize = 28
                sharedStorageNumber = dataview_get_int64(view, contentOffset)
                sharedMapNumber = null
                sharedCollectionNumber = null
                sharedFeatureNumber = null
                dataOffset = contentOffset + 8
            }
            2 -> { // 192-bit (collection-number, feature-number, version, uid)
                contentOffset = BinaryUtil.getContentOffset(view)
                storageNumberOffset = -1
                mapNumberOffset = -1
                collectionNumberOffset = 0
                featureNumberOffset = 4
                txnOffset = 12
                uidOffset = 20
                entrySize = 24
                sharedStorageNumber = dataview_get_int64(view, contentOffset)
                sharedMapNumber = dataview_get_int32(view, contentOffset + 8)
                sharedCollectionNumber = null
                sharedFeatureNumber = null
                dataOffset = contentOffset + 12
            }
            3 -> { // 160-bit (feature-number, version, uid)
                contentOffset = BinaryUtil.getContentOffset(view)
                storageNumberOffset = -1
                mapNumberOffset = -1
                collectionNumberOffset = -1
                featureNumberOffset = 0
                txnOffset = 8
                uidOffset = 16
                entrySize = 20
                sharedStorageNumber = dataview_get_int64(view, contentOffset)
                sharedMapNumber = dataview_get_int32(view, contentOffset + 8)
                sharedCollectionNumber = dataview_get_int32(view, contentOffset + 12)
                sharedFeatureNumber = null
                dataOffset = contentOffset + 16
            }
            4 -> { // 96-bit (version, uid)
                contentOffset = BinaryUtil.getContentOffset(view)
                storageNumberOffset = -1
                mapNumberOffset = -1
                collectionNumberOffset = -1
                featureNumberOffset = -1
                txnOffset = 0
                uidOffset = 8
                entrySize = 12
                sharedStorageNumber = dataview_get_int64(view, contentOffset)
                sharedMapNumber = dataview_get_int32(view, contentOffset + 8)
                sharedCollectionNumber = dataview_get_int32(view, contentOffset + 12)
                sharedFeatureNumber = dataview_get_int64(view, contentOffset + 16)
                dataOffset = contentOffset + 24
            }
            else -> throw NakshaException(ILLEGAL_ARGUMENT, "The header of the binary stores subtype $subtype, which is invalid")
        }
    }
    private val last: Int = if (length <= 0 || bytes.size < 36) 0 else dataOffset + (length-1) * entrySize
    private var tupleNumberCache = EMPTY

    /**
     * Can be used to disable [TupleNumber] caching.
     * @since 3.0
     */
    var disableCache: Boolean = false

    /**
     * Disable the caching of [TupleNumber].
     * @return this
     * @since 3.0
     */
    fun withDisabledCache(): TupleNumberBinaryArray {
        tupleNumberCache = EMPTY
        disableCache = true
        return this
    }

    /**
     * Clears the [TupleNumber] cache that is automatically generated, when [get] is invoked.
     * @since 3.0
     */
    fun clearCache(): TupleNumberBinaryArray {
        tupleNumberCache = EMPTY
        return this
    }

    /**
     * The amount of [tuple-numbers][TupleNumber] in the array.
     * @since 3.0
     */
    override val size: Int
        get() = length

    @Suppress("NON_EXPORTABLE_TYPE")
    override fun containsAll(elements: Collection<TupleNumber?>): Boolean {
        for (element in elements) {
            if (!contains(element)) return false
        }
        return true
    }

    override fun contains(element: TupleNumber?): Boolean = indexOf(element) >= 0

    @Suppress("NOTHING_TO_INLINE")
    private inline fun offset(index: Int): Int = dataOffset + index * entrySize

    /**
     * Returns the tuple-number from the given index.
     * @param index the index.
     * @return the tuple-number or _null_, if out of bounds.
     * @since 3.0
     */
    override operator fun get(index: Int): TupleNumber? {
        if (index < tupleNumberCache.size) {
            val cached = tupleNumberCache[index]
            if (cached != null) return cached
        }
        val offset = offset(index)
        if (offset < 0 || offset > last) return null
        val storageNumber = sharedStorageNumber ?: dataview_get_int64(view, offset + storageNumberOffset)
        val mapNumber = sharedMapNumber ?: dataview_get_int32(view, offset + mapNumberOffset)
        val collectionNumber = sharedCollectionNumber ?: dataview_get_int32(view, offset + collectionNumberOffset)
        val featureNumber = sharedFeatureNumber ?: dataview_get_int64(view, offset + featureNumberOffset)
        val txn = dataview_get_int64(view, offset + txnOffset)
        val uid = dataview_get_int32(view, offset + uidOffset)
        val tupleNumber = TupleNumber(storageNumber, mapNumber, collectionNumber, featureNumber, Version(txn), uid)
        if (!disableCache) {
            var cache = tupleNumberCache
            if (index <= cache.size) { // Note: This only happens, when being EMPTY
                cache = cache.copyOf(length)
                tupleNumberCache = cache
            }
            cache[index] = tupleNumber
        }
        return tupleNumber
    }

    /**
     * Returns the storage-number from the given index.
     * @param index the index.
     * @return the storage-number.
     * @since 3.0
     */
    fun getStorageNumber(index: Int): Int64 {
        val offset = offset(index)
        if (offset < 0 || offset > last) throw IndexOutOfBoundsException()
        return sharedStorageNumber ?: dataview_get_int64(view, offset + storageNumberOffset)
    }

    /**
     * Returns the map-number from the given index.
     * @param index the index.
     * @return the map-number.
     * @since 3.0
     */
    fun getMapNumber(index: Int): Int {
        val offset = offset(index)
        if (offset < 0 || offset > last) throw IndexOutOfBoundsException()
        return sharedMapNumber ?: dataview_get_int32(view, offset + mapNumberOffset)
    }

    /**
     * Returns the collection-number from the given index.
     * @param index the index.
     * @return the collection-number.
     * @since 3.0
     */
    fun getCollectionNumber(index: Int): Int {
        val offset = offset(index)
        if (offset < 0 || offset > last) throw IndexOutOfBoundsException()
        return sharedMapNumber ?: dataview_get_int32(view, offset + collectionNumberOffset)
    }

    /**
     * Returns the feature-number from the given index.
     * @param index the index.
     * @return the feature-number.
     * @since 3.0
     */
    fun getFeatureNumber(index: Int): Int64 {
        val offset = offset(index)
        if (offset < 0 || offset > last) throw IndexOutOfBoundsException()
        return sharedFeatureNumber ?: dataview_get_int64(view, offset + featureNumberOffset)
    }

    /**
     * Returns the partition-number from the given index.
     * @param index the index.
     * @return the partition-number (`0..65535`).
     * @since 3.0
     */
    fun getPartitionNumber(index: Int): Int = Naksha.partitionNumber(getFeatureNumber(index))

    /**
     * Returns the transaction-number from the given index.
     * @param index the index.
     * @return the transaction-number.
     * @since 3.0
     */
    fun getTxn(index: Int): Int64 {
        val offset = offset(index)
        if (offset < 0 || offset > last) throw IndexOutOfBoundsException()
        return dataview_get_int64(view, offset + txnOffset)
    }

    /**
     * Returns the uid at the given index.
     * @param index the index.
     * @return the uid.
     * @since 3.0
     */
    fun getUid(index: Int): Int {
        val offset = offset(index)
        if (offset < 0 || offset > last) throw IndexOutOfBoundsException()
        return dataview_get_int32(view, offset + uidOffset)
    }

    /**
     * Compress this byte-array and return the compressed version (this is helpful for caching).
     * @return the compressed tuple-number array.
     * @since 3.0
     */
    fun gzip(): ByteArray = Platform.gzipDeflate(bytes)

    /**
     * Unpack this into an array of [tuple-numbers][TupleNumber].
     * @return an array of [tuple-numbers][TupleNumber].
     * @since 3.0
     */
    fun toArray(): Array<TupleNumber> = Array(bytes.size) { get(it)!! }

    /**
     * Calculate the MD5 hash above the [bytes].
     * @return the MD5 hash above the [bytes].
     * @since 3.0
     */
    fun md5(): ByteArray = Platform.md5(bytes)

    /**
     * Helper method to convert this binary into a [TupleNumberList].
     *
     * @param from the index of the first entry to convert.
     * @param to the index of the first entry **not** to convert.
     * @return the [TupleNumberList].
     * @since 3.0
     */
    @JvmOverloads
    fun toTupleNumberList(from:Int=0, to:Int=size): TupleNumberList = TupleNumberList.fromByteArray(this, from, to)

    /**
     * Helper method to convert this binary into a [FeatureTupleList].
     *
     * @param from the index of the first entry to convert.
     * @param to the index of the first entry **not** to convert.
     * @return the [FeatureTupleList].
     * @since 3.0
     */
    @JvmOverloads
    fun toFeatureTupleList(from:Int=0, to:Int=size): FeatureTupleList = FeatureTupleList.fromByteArray(this, from, to)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as TupleNumberBinaryArray
        return bytes.contentEquals(other.bytes)
    }
    override fun hashCode(): Int = bytes.contentHashCode()

    override fun isEmpty(): Boolean = size == 0

    internal class TupleNumberBinaryArrayIterator internal constructor(
        private val binary: TupleNumberBinaryArray,
        private val from: Int = 0,
        private val to: Int = binary.size,
        private var i: Int = from,
    ) : ListIterator<TupleNumber?> {

        override fun hasNext(): Boolean = i in from until to

        override fun hasPrevious(): Boolean = (i-1) in from until to

        override fun next(): TupleNumber? = binary[i++]

        override fun nextIndex(): Int = i + 1

        override fun previous(): TupleNumber? = binary[--i]

        override fun previousIndex(): Int = i - 1

    }

    @Suppress("NON_EXPORTABLE_TYPE")
    override fun iterator(): Iterator<TupleNumber?> = TupleNumberBinaryArrayIterator(this)

    @Suppress("NON_EXPORTABLE_TYPE")
    override fun listIterator(): ListIterator<TupleNumber?> = TupleNumberBinaryArrayIterator(this)

    @Suppress("NON_EXPORTABLE_TYPE")
    override fun listIterator(index: Int): ListIterator<TupleNumber?> = TupleNumberBinaryArrayIterator(this, from=index)

    override fun subList(fromIndex: Int, toIndex: Int): List<TupleNumber?> {
        val list = ArrayList<TupleNumber?>(toIndex - fromIndex)
        for (i in fromIndex until toIndex) list.add(this[i])
        return list
    }
    override fun lastIndexOf(element: TupleNumber?): Int {
        if (element == null) return -1
        for (i in size - 1 downTo 0) {
            if (element.storageNumber == getStorageNumber(i)
                && element.mapNumber == getMapNumber(i)
                && element.collectionNumber == getCollectionNumber(i)
                && element.featureNumber == getFeatureNumber(i)
                && element.version.txn == getTxn(i)
                && element.uid == getUid(i)) return i
        }
        return -1
    }

    override fun indexOf(element: TupleNumber?): Int {
        if (element == null) return -1
        for (i in 0 until size) {
            if (element.storageNumber == getStorageNumber(i)
                && element.mapNumber == getMapNumber(i)
                && element.collectionNumber == getCollectionNumber(i)
                && element.featureNumber == getFeatureNumber(i)
                && element.version.txn == getTxn(i)
                && element.uid == getUid(i)) return i
        }
        return -1
    }

    override fun toString(): String = bytes.contentToString()

    companion object TupleNumberBinaryArray_C {
        /**
         * The [PlatformType] of [TupleNumberBinaryArray].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(TupleNumberBinaryArray::class).withPackageName(PACKAGE_NAME)

        /**
         * The default empty tuple-number cache.
         */
        private val EMPTY = emptyArray<TupleNumber?>()

        /**
         * Return a [TupleNumberBinaryArray] from a compressed byte-array.
         * @param compressed the compressed tuple-number array.
         * @return the [TupleNumberBinaryArray].
         * @since 3.0
         */
        @JvmStatic
        @JsStatic
        fun fromGzip(compressed: ByteArray): TupleNumberBinaryArray
            = fromByteArray(Platform.gzipInflate(compressed))

        /**
         * Return a [TupleNumberBinaryArray] from a raw byte-array.
         * @param bytes the byte-array that contains the [tuple-numbers][TupleNumber]
         * @param from the first byte to read _(inclusive)_, defaults to `0`
         * @param to the first byte **not** to read _(exclusive)_, defaults to `bytes.size`
         * @return the [TupleNumberBinaryArray] mapping the given byte-array
         */
        @JsName("fromByteArray")
        @JvmOverloads
        fun fromByteArray(bytes: ByteArray, from:Int = 0, to:Int = bytes.size)
            = TupleNumberBinaryArray(Platform.newDataView(bytes, from, to - from))
    }
}
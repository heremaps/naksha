@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Id
import naksha.base.Base
import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.base.NakshaException
import naksha.base.TupleNumber
import naksha.base.getInt32Be
import naksha.base.getInt64Be
import kotlin.js.JsExport
import kotlin.jvm.JvmOverloads

/**
 * A helper that allows reading a binary encoded array of [naksha.base.TupleNumber]'s.
 *
 * This binary can be serialized technically, but just using the [Platform.toJSON][naksha.base.BaseCompanion.toJSON] method, because it requires a binary encoding, which means serialization and deserialization into a [Data URL](https://developer.mozilla.org/en-US/docs/Web/URI/Reference/Schemes/data), which is normally not supported out of the box by standard JSON parsers/serializers, it is a proprietary extension to the JSON standard, the same way that 64-bit integers are handled as special [Data URL](https://developer.mozilla.org/en-US/docs/Web/URI/Reference/Schemes/data) by [Platform.toJSON][naksha.base.BaseCompanion.toJSON].
 *
 * @since 3.0
 */
@JsExport
data class TupleNumberBinaryArray(
    /**
     * The underlying byte-array.
     * @since 3.0
     */
    val bytes: ByteArray
) : List<TupleNumber?> {

    private val length: Int = BinaryUtil.getLength(bytes)
    private val contentOffset: Int
    private val sharedStorageNumber: Long?
    private val sharedMapNumber: Int?
    private val sharedCollectionNumber: Int?
    private val sharedFeatureNumber: Long?
    private val entrySize: Int
    private val dataOffset: Int
    private val storageNumberOffset: Int
    private val mapNumberOffset: Int
    private val collectionNumberOffset: Int
    private val featureNumberOffset: Int
    private val txnOffset: Int
    init {
        when (val subtype = BinaryUtil.getSubType(bytes)) {
            0 -> { // 256-bit (storage-number, map-number, collection-number, feature-number, txn)
                contentOffset = BinaryUtil.getContentOffset(bytes)
                storageNumberOffset = 0
                mapNumberOffset = 8
                collectionNumberOffset = 12
                featureNumberOffset = 16
                txnOffset = 24
                entrySize = 32
                sharedStorageNumber = null
                sharedMapNumber = null
                sharedCollectionNumber = null
                sharedFeatureNumber = null
                dataOffset = contentOffset
            }
            1 -> { // 192-bit (map-number, collection-number, feature-number, txn)
                contentOffset = BinaryUtil.getContentOffset(bytes)
                storageNumberOffset = -1
                mapNumberOffset = 0
                collectionNumberOffset = 4
                featureNumberOffset = 12
                txnOffset = 20
                entrySize = 24
                sharedStorageNumber = bytes.getInt64Be(contentOffset)
                sharedMapNumber = null
                sharedCollectionNumber = null
                sharedFeatureNumber = null
                dataOffset = contentOffset + 8
            }
            2 -> { // 160-bit (collection-number, feature-number, txn)
                contentOffset = BinaryUtil.getContentOffset(bytes)
                storageNumberOffset = -1
                mapNumberOffset = -1
                collectionNumberOffset = 0
                featureNumberOffset = 4
                txnOffset = 12
                entrySize = 20
                sharedStorageNumber = bytes.getInt64Be( contentOffset)
                sharedMapNumber = bytes.getInt32Be(contentOffset + 8)
                sharedCollectionNumber = null
                sharedFeatureNumber = null
                dataOffset = contentOffset + 12
            }
            3 -> { // 128-bit (feature-number, txn)
                contentOffset = BinaryUtil.getContentOffset(bytes)
                storageNumberOffset = -1
                mapNumberOffset = -1
                collectionNumberOffset = -1
                featureNumberOffset = 0
                txnOffset = 8
                entrySize = 16
                sharedStorageNumber = bytes.getInt64Be(contentOffset)
                sharedMapNumber = bytes.getInt32Be(contentOffset + 8)
                sharedCollectionNumber = bytes.getInt32Be(contentOffset + 12)
                sharedFeatureNumber = null
                dataOffset = contentOffset + 16
            }
            4 -> { // 64-bit (txn)
                contentOffset = BinaryUtil.getContentOffset(bytes)
                storageNumberOffset = -1
                mapNumberOffset = -1
                collectionNumberOffset = -1
                featureNumberOffset = -1
                txnOffset = 0
                entrySize = 8
                sharedStorageNumber = bytes.getInt64Be(contentOffset)
                sharedMapNumber = bytes.getInt32Be(contentOffset + 8)
                sharedCollectionNumber = bytes.getInt32Be(contentOffset + 12)
                sharedFeatureNumber = bytes.getInt64Be(contentOffset + 16)
                dataOffset = contentOffset + 24
            }
            else -> throw NakshaException(ILLEGAL_ARGUMENT, "The header of the binary stores subtype $subtype, which is invalid")
        }
    }
    private val last: Int = if (length <= 0 || bytes.size < 32) 0 else dataOffset + (length-1) * entrySize
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
        if (offset !in 0..last) return null
        val storageNumber = sharedStorageNumber ?: bytes.getInt64Be(offset + storageNumberOffset)
        val mapNumber = sharedMapNumber ?: bytes.getInt32Be(offset + mapNumberOffset)
        val collectionNumber = sharedCollectionNumber ?: bytes.getInt32Be(offset + collectionNumberOffset)
        val featureNumber = sharedFeatureNumber ?: bytes.getInt64Be(offset + featureNumberOffset)
        val txn = bytes.getInt64Be(offset + txnOffset)
        val tupleNumber = TupleNumber(storageNumber, mapNumber, collectionNumber, featureNumber, txn)
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
    fun getStorageNumber(index: Int): Long {
        val offset = offset(index)
        if (offset !in 0..last) throw IndexOutOfBoundsException()
        return sharedStorageNumber ?: bytes.getInt64Be(offset + storageNumberOffset)
    }

    /**
     * Returns the map-number from the given index.
     * @param index the index.
     * @return the map-number.
     * @since 3.0
     */
    fun getMapNumber(index: Int): Int {
        val offset = offset(index)
        if (offset !in 0..last) throw IndexOutOfBoundsException()
        return sharedMapNumber ?: bytes.getInt32Be(offset + mapNumberOffset)
    }

    /**
     * Returns the collection-number from the given index.
     * @param index the index.
     * @return the collection-number.
     * @since 3.0
     */
    fun getCollectionNumber(index: Int): Int {
        val offset = offset(index)
        if (offset !in 0..last) throw IndexOutOfBoundsException()
        return sharedMapNumber ?: bytes.getInt32Be(offset + collectionNumberOffset)
    }

    /**
     * Returns the feature-number from the given index.
     * @param index the index.
     * @return the feature-number.
     * @since 3.0
     */
    fun getFeatureNumber(index: Int): Long {
        val offset = offset(index)
        if (offset !in 0..last) throw IndexOutOfBoundsException()
        return sharedFeatureNumber ?: bytes.getInt64Be(offset + featureNumberOffset)
    }

    /**
     * Returns the partition-number from the given index.
     * @param index the index.
     * @return the partition-number (`0..65535`).
     * @since 3.0
     */
    fun getPartitionNumber(index: Int): Int = Id.partitionNumber(getFeatureNumber(index))

    /**
     * Returns the transaction-number from the given index.
     * @param index the index.
     * @return the transaction-number.
     * @since 3.0
     */
    fun getTxn(index: Int): Long {
        val offset = offset(index)
        if (offset !in 0..last) throw IndexOutOfBoundsException()
        return bytes.getInt64Be( offset + txnOffset)
    }

    /**
     * Compress this byte-array and return the compressed version (this is helpful for caching).
     * @return the compressed tuple-number array.
     * @since 3.0.0
     */
    fun gzip(): ByteArray = Base.gzipDeflate(bytes)

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
    fun md5(): ByteArray = Base.md5(bytes)

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
            if (element.databaseNumber == getStorageNumber(i)
                && element.catalogNumber == getMapNumber(i)
                && element.collectionNumber == getCollectionNumber(i)
                && element.featureNumber == getFeatureNumber(i)
                && element.version == getTxn(i)) return i
        }
        return -1
    }

    override fun indexOf(element: TupleNumber?): Int {
        if (element == null) return -1
        for (i in 0 until size) {
            if (element.databaseNumber == getStorageNumber(i)
                && element.catalogNumber == getMapNumber(i)
                && element.collectionNumber == getCollectionNumber(i)
                && element.featureNumber == getFeatureNumber(i)
                && element.version == getTxn(i)) return i
        }
        return -1
    }

    override fun toString(): String = bytes.contentToString()

    companion object TupleNumberByteArray_C {
        /**
         * The default empty tuple-number cache.
         */
        private val EMPTY = emptyArray<TupleNumber?>()
    }
}
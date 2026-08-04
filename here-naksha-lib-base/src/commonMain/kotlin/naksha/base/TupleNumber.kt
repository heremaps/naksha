@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * The in-memory representation of the unique address of a `Tuple`.
 *
 * The full qualified `Tuple` address is a 256-bit value _(32 byte)_, persisting out of the database-number, catalog-number, collection-number, feature-number, and version. Note that the lower two bits of version encode the [action][Action].
 *
 * The tuple-number is stringified either into URN:
 * ```
 * urn:naksha:tn:{database-number}:{catalog-number}:{collection-number}:{feature-number}:{version}
 * ```
 *
 * There are no two `Tuple` with the same [tuple-number][TupleNumber]; world-wide.
 * @since 3.0
 */
@JsExport
data class TupleNumber(
    /**
     * The database-number, uniquely identifies the storage where the tuple is stored.
     * @since 3.0
     */
    @JvmField val databaseNumber: Long,

    /**
     * The catalog-number of the map in which the tuple is stored within the storage.
     * @since 3.0.0
     */
    @JvmField val catalogNumber: Int,

    /**
     * The collection-number of the collection in which the tuple is stored within the storage.
     * @since 3.0
     */
    @JvmField val collectionNumber: Int,

    /**
     * The feature-number.
     * @since 3.0
     */
    @JvmField val featureNumber: Long,

    /**
     * The version _(transaction)_ of which the `Tuple` is part of.
     * The lower 2 bits of [Version.number] encode the [Action].
     * @since 3.0
     * @see [Version.HEAD]
     */
    @JvmField val version: Long,
) : Comparable<TupleNumber> {

    /**
     * Create a [TupleNumber] from the given `id`'s.
     * @param databaseId the `id` of the database.
     * @param catalogId the `id` of the catalog.
     * @param collectionId the `id` of the collection.
     * @param featureId the `id` of the feature.
     * @param version the version.
     */
    @JsName("tupleNumberByIds")
    constructor(databaseId: Id, catalogId: Id, collectionId: Id, featureId: Id, version: Long)
        : this(databaseId.number, catalogId.intValue, collectionId.intValue, featureId.number, version)

    /**
     * Create a [TupleNumber] from the given `id`'s.
     * @param databaseId the `id` of the database.
     * @param catalogId the `id` of the catalog.
     * @param collectionId the `id` of the collection.
     * @param featureNumber the feature-number.
     * @param version the version.
     */
    @JsName("tupleNumberByDatabaseCatalogAndCollectionId")
    constructor(databaseId: Id, catalogId: Id, collectionId: Id, featureNumber: Long, version: Long)
            : this(databaseId.number, catalogId.intValue, collectionId.intValue, featureNumber, version)

    /**
     * Create a [TupleNumber] from the given `id`'s.
     * @param databaseId the `id` of the database.
     * @param catalogId the `id` of the catalog.
     * @param collectionNumber the collection-number.
     * @param featureNumber the feature-number.
     * @param version the version.
     */
    @JsName("tupleNumberByDatabaseAndCatalogId")
    constructor(databaseId: Id, catalogId: Id, collectionNumber: Int, featureNumber: Long, version: Long)
            : this(databaseId.number, catalogId.intValue, collectionNumber, featureNumber, version)

    /**
     * Create a [TupleNumber] from the given `id`'s.
     * @param databaseId the `id` of the database.
     * @param catalogNumber the catalog-number.
     * @param collectionNumber the collection-number.
     * @param featureNumber the feature-number.
     * @param version the version.
     */
    @JsName("tupleNumberByDatabaseId")
    constructor(databaseId: Id, catalogNumber: Int, collectionNumber: Int, featureNumber: Long, version: Long)
            : this(databaseId.number, catalogNumber, collectionNumber, featureNumber, version)

    /**
     * The partition-number of the `Tuple`, a value between `0` and `65536` _(exclusive)_.
     * @since 3.0
     */
    val partitionNumber: Int
        get() = featureNumber.toInt() and 0xffff

    /**
     * The [Action] applied to generate the `Tuple` referred by this [TupleNumber].
     * Decoded from the lower 2 bits of [version].
     * @since 3.0
     */
    val action: Action
        get() = Action.fromVersion(version)

    /**
     * Tests if the `Tuple` is a tombstone, so a deleted state.
     * @since 3.0
     */
    fun isDeleted(): Boolean = action == Action.DELETE

    /**
     * Calculates the distribution partition-index where this `Tuple` will be located.
     *
     * If the given partitions are less than `2`, the method always returns `-1`. If the number is bigger than `65536` the result will be mapped back into the range between `0` and `65536` _(exclusive)_.
     * @param partitions the number of partitions
     * @return the partition-index, a value between `0` and `partitions - 1` _(maximal 65535)_; or `-1` if there are no distribution partitions.
     * @since 3.0
     */
    fun partitionIndex(partitions: Int): Int = if (partitions < 2) -1 else (partitionNumber % partitions) and 0xffff

    override fun hashCode(): Int = version.hashCode()

    override fun compareTo(other: TupleNumber): Int {
        var i64_diff = databaseNumber - other.databaseNumber
        if (i64_diff < 0) return -1
        if (i64_diff > 0) return 1
        var i32_diff = catalogNumber - other.catalogNumber
        if (i32_diff < 0) return -1
        if (i32_diff > 0) return 1
        i32_diff = collectionNumber - other.collectionNumber
        if (i32_diff < 0) return -1
        if (i32_diff > 0) return 1
        i32_diff = partitionNumber - other.partitionNumber
        if (i32_diff < 0) return -1
        if (i32_diff > 0) return 1
        i64_diff = featureNumber - other.featureNumber
        if (i64_diff < 0) return -1
        if (i64_diff > 0) return 1
        i32_diff = version.compareTo(other.version)
        if (i32_diff < 0) return -1
        if (i32_diff > 0) return 1
        return 0
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is TupleNumber
            && databaseNumber == other.databaseNumber
            && catalogNumber == other.catalogNumber
            && collectionNumber == other.collectionNumber
            && featureNumber == other.featureNumber
            && version == other.version
    }

    private lateinit var _string: String

    /**
     * Convert this [TupleNumber] into a [URN](https://datatracker.ietf.org/doc/html/rfc8141), the exact format will be:
     * ```
     * urn:naksha:tn:{storage-number}:{map-number}:{collection-number}:{feature-number}:{version}
     * ```
     * @return the [URN](https://datatracker.ietf.org/doc/html/rfc8141) that describes this state world-wide uniquely.
     * @since 3.0
     */
    override fun toString(): String {
        if (!this::_string.isInitialized) {
            _string = "urn:naksha:tn:$databaseNumber:$catalogNumber:$collectionNumber:$featureNumber:$version"
        }
        return _string
    }

    /**
     * Tests if this [TupleNumber] is [HEAD].
     * @return `true` if this is [HEAD]; `false` otherwise.
     * @since 3.0
     */
    fun isHead(): Boolean = this == HEAD

    /**
     * Tests if the given tuple-number is the same feature, just potentially in a different state _(version)_.
     * @param other the other [TupleNumber].
     * @return true if this [TupleNumber] is the same feature, so has the same database-, catalog-, collection-, and feature-number; false otherwise _(ignores only version)_.
     */
    fun isSameFeature(other: TupleNumber?): Boolean = other != null &&
        this.databaseNumber == other.databaseNumber &&
        this.catalogNumber == other.catalogNumber &&
        this.collectionNumber == other.collectionNumber &&
        this.featureNumber == other.featureNumber

    /**
     * Encode this [tuple-number][TupleNumber] into its binary representation, not storing the binary header upfront, encodes:
     * - `txn` _(aka [Version])_
     * @since 3.0
     */
    fun toB64(): ByteArray = toByteArray(TupleNumberVariant.B64)

    /**
     * Encode this [tuple-number][TupleNumber] into its binary representation, not storing the binary header upfront, encodes:
     * - `feature-number`
     * - `txn` _(aka [Version])_
     * @since 3.0
     */
    fun toB128(): ByteArray = toByteArray(TupleNumberVariant.B128)

    /**
     * Encode this [tuple-number][TupleNumber] into its binary representation, not storing the binary header upfront, encodes:
     * - `collection-number`
     * - `feature-number`
     * - `txn` _(aka [Version])_
     * @since 3.0
     */
    fun toB160(): ByteArray = toByteArray(TupleNumberVariant.B160)

    /**
     * Encode this [tuple-number][TupleNumber] into its binary representation, not storing the binary header upfront, encodes:
     * - `map-number`
     * - `collection-number`
     * - `feature-number`
     * - `txn` _(aka [Version])_
     * @since 3.0
     */
    fun toB192(): ByteArray = toByteArray(TupleNumberVariant.B192)

    /**
     * Encode this [tuple-number][TupleNumber] into its binary representation, not storing the binary header upfront, encodes:
     * - `storage-number`
     * - `map-number`
     * - `collection-number`
     * - `feature-number`
     * - `txn` _(aka [Version])_
     * @since 3.0
     */
    fun toB256(): ByteArray = toByteArray(TupleNumberVariant.B256)

    /**
     * Encode this [tuple-number][TupleNumber] into its binary representation, not storing the binary header upfront.
     *
     * This method is internally used to save space.
     *
     * @param variant the [TupleNumberVariant] to use for the encoding.
     * @return the binary encoded [tuple-number][TupleNumber].
     * @since 3.0
     */
    fun toByteArray(variant: TupleNumberVariant): ByteArray {
        val byteArray = ByteArray(variant.encodingBytes)
        var offset = 0
        if (variant.encodeDatabaseNumber()) {
            byteArray.setInt64Be(offset, databaseNumber)
            offset += 8
        }
        if (variant.encodeCatalogNumber()) {
            byteArray.setInt32Be(offset, catalogNumber)
            offset += 4
        }
        if (variant.encodeCollectionNumber()) {
            byteArray.setInt32Be(offset, collectionNumber)
            offset += 4
        }
        if (variant.encodeFeatureNumber()) {
            byteArray.setInt64Be(offset, featureNumber)
            offset += 8
        }
        byteArray.setInt64Be(offset, version)
        return byteArray
    }

    companion object TupleNumber_C {
        internal const val STORAGE_NUMBER = 0
        internal const val MAP_NUMBER = 1
        internal const val COLLECTION_NUMBER = 2
        internal const val FEATURE_NUMBER = 3
        internal const val VERSION = 4
        internal const val TN_PARTS = 5

        internal const val URN = 0
        internal const val NAKSHA = 1
        internal const val TN = 2
        internal const val URN_TN_OFFSET = 3
        internal const val URN_PARTS = TN_PARTS + 3 // = 8

        internal const val GUID = 2
        internal const val ID = 3
        internal const val GUID_TN_OFFSET = 4
        internal const val GUID_PARTS = TN_PARTS + 4 // = 9
        internal const val SHORT_GUID_PARTS = 4 // 4 -> "urn:naksha:guid:{id}"

        /**
         * Helper to create a new tuple-number based upon some values from an existing.
         *
         * @param tn the tuple-number from which to copy.
         * @param version if not `null`, overrides `tn.version`
         * @param featureNumber if not `null`, overrides `tn.featureNumber`
         * @param collectionNumber if not `null`, overrides `tn.collectionNumber`
         * @param catalogNumber if not `null`, overrides `tn.mapNumber`
         * @param databaseNumber if not `null`, overrides `tn.storageNumber`
         *
         */
        @JsStatic
        @JvmStatic
        @JvmOverloads
        fun copy(
            tn: TupleNumber,
            version: Long = tn.version,
            featureNumber: Long = tn.featureNumber,
            collectionNumber: Int = tn.collectionNumber,
            catalogNumber: Int = tn.catalogNumber,
            databaseNumber: Long = tn.databaseNumber,
        ) = TupleNumber(databaseNumber, catalogNumber, collectionNumber,featureNumber, version)

        /**
         * The _HEAD_ [TupleNumber], to be used when a [tuple-number][TupleNumber] is not yet available.
         *
         * This happens for various reasons, for example when a `Tuple` is created in the client at runtime, and not yet persisted in any storage, therefore does not yet have a valid tuple-number.
         * @since 3.0
         */
        val HEAD = TupleNumber(0L, 0, 0, 0L, Version.HEAD.number)

        /**
         * Restore a [TupleNumber] from a binary encoding.
         * @param bytes the binary to read.
         * @param offset the index of the first byte to read.
         * @param variant the variant to read.
         * @param tn the [TupleNumber] from which to copy missing values.
         */
        @JsName("ofByteArray")
        @JsStatic
        @JvmStatic
        fun fromByteArray(
            bytes: ByteArray,
            offset: Int,
            variant: TupleNumberVariant,
            tn: TupleNumber
        ): TupleNumber = fromByteArray(bytes, offset, variant, tn.databaseNumber, tn.catalogNumber, tn.collectionNumber, tn.featureNumber)

        /**
         * @see fromByteArray
         */
        fun fromB256(bytes: ByteArray, offset: Int)
                = fromByteArray(bytes, offset, TupleNumberVariant.B256)
        /**
         * @see fromByteArray
         */
        fun fromB192(bytes: ByteArray, offset: Int, catalogNumber: Long)
                = fromByteArray(bytes, offset, TupleNumberVariant.B192, catalogNumber)
        /**
         * @see fromByteArray
         */
        fun fromB160(bytes: ByteArray, offset: Int, databaseNumber: Long, catalogNumber: Int)
                = fromByteArray(bytes, offset, TupleNumberVariant.B160, databaseNumber, catalogNumber)
        /**
         * @see fromByteArray
         */
        fun fromB128(bytes: ByteArray, offset: Int, databaseNumber: Long, catalogNumber: Int, collectionNumber: Int)
                = fromByteArray(bytes, offset, TupleNumberVariant.B128, databaseNumber, catalogNumber, collectionNumber)
        /**
         * @see fromByteArray
         */
        fun fromB64(bytes: ByteArray, offset: Int, databaseNumber: Long, catalogNumber: Int, collectionNumber: Int, featureNumber: Long)
                = fromByteArray(bytes, offset, TupleNumberVariant.B64, databaseNumber, catalogNumber, collectionNumber, featureNumber)

        /**
         * Restore a [TupleNumber] from a binary encoding.
         *
         * @param bytes the binary to read.
         * @param variant the variant to read.
         * @param databaseNumber if the binary does not encode the database-number _(anything other than [TupleNumberVariant.TupleNumberVariant_C.B256])_, so variant is [TupleNumberVariant.TupleNumberVariant_C.B64], [TupleNumberVariant.TupleNumberVariant_C.B128], [TupleNumberVariant.TupleNumberVariant_C.B160], or [TupleNumberVariant.TupleNumberVariant_C.B192].
         * @param catalogNumber if the binary does not encode the catalog-number, so variant is [TupleNumberVariant.TupleNumberVariant_C.B64], [TupleNumberVariant.TupleNumberVariant_C.B128], or [TupleNumberVariant.TupleNumberVariant_C.B160].
         * @param collectionNumber if the binary does not encode the collection-number, so variant is [TupleNumberVariant.TupleNumberVariant_C.B64] or [TupleNumberVariant.TupleNumberVariant_C.B128].
         * @param featureNumber if the binary does not encode the feature-number, so variant is [TupleNumberVariant.TupleNumberVariant_C.B64].
         */
        @JsStatic
        @JvmStatic
        @JvmOverloads
        fun fromByteArray(
            bytes: ByteArray,
            offset: Int = 0,
            variant: TupleNumberVariant = TupleNumberVariant.fromValue(bytes.size - offset),
            databaseNumber: Long = 0L,
            catalogNumber: Int = 0,
            collectionNumber: Int = 0,
            featureNumber: Long = 0L
        ): TupleNumber {
            var offset = offset
            var databaseNumber = databaseNumber
            if (variant.encodeDatabaseNumber()) {
                databaseNumber = bytes.getInt64Be(offset)
                offset += 8
            }
            var catalogNumber = catalogNumber
            if (variant.encodeCatalogNumber()) {
                catalogNumber = bytes.getInt32Be(offset)
                offset += 4
            }
            var collectionNumber = collectionNumber
            if (variant.encodeCollectionNumber()) {
                collectionNumber = bytes.getInt32Be(offset)
                offset += 4
            }
            var featureNumber = featureNumber
            if (variant.encodeFeatureNumber()) {
                featureNumber = bytes.getInt64Be(offset)
                offset += 8
            }
            val version = bytes.getInt64Be(offset)
            return TupleNumber(databaseNumber, catalogNumber, collectionNumber, featureNumber, version)
        }

        /**
         * Convert the given value into a [TupleNumber] if possible. If the value given is a [TupleNumber] it is returned as is, otherwise strings and byte-arrays are parsed, when possible.
         * @param value the value to convert into a [TupleNumber].
         * @return the value as [TupleNumber] or `null`, if the given value is no valid tuple-number.
         * @since 3.0
         */
        @JsStatic
        @JvmStatic
        fun toTupleNumber(value: Any?): TupleNumber? {
            if (value is TupleNumber) return value
            if (value is ByteArray) return if (value.size == 32) fromB256(value, 0) else null
            if (value is TupleNumber) return value
            if (value !is CharSequence) return null
            val parts = value.split(':')
            return when (parts.size) {
                URN_PARTS -> if (parts[URN] != "urn" || parts[NAKSHA] != "naksha" || parts[TN] != "tn") null else fromParts(parts, URN_TN_OFFSET)
                GUID_PARTS -> if (parts[URN] != "urn" || parts[NAKSHA] != "naksha" || parts[GUID] != "guid") null else fromParts(parts, GUID_TN_OFFSET)
                else -> null
            }
        }

        /**
         * Restore a [TupleNumber] from the stringified version.
         * @param string the string to parse.
         * @return the deserialized [TupleNumber].
         * @throws NakshaException with error [NakshaError.ILLEGAL_ARGUMENT] if the given characters do not contain a valid tuple-number.
         */
        @JsStatic
        @JvmStatic
        fun fromString(string: String): TupleNumber {
            val parts = string.split(':')
            return when (parts.size) {
                SHORT_GUID_PARTS -> { // 4
                    throw illegalArg("Invalid tuple-number, short GUID can't be parsed: $string")
                }
                URN_PARTS -> { // 5
                    if (parts[URN] != "urn" || parts[NAKSHA] != "naksha" || parts[TN] != "tn") {
                        throw illegalArg("Invalid tuple-number URN: $string")
                    }
                    fromParts(parts, URN_TN_OFFSET)
                }
                GUID_PARTS -> { // 9
                    if (parts[URN] != "urn" || parts[NAKSHA] != "naksha" || parts[GUID] != "guid") {
                        throw illegalArg("Invalid GUID: $string")
                    }
                    // We ignore feature-id.
                    fromParts(parts, GUID_TN_OFFSET)
                }
                else -> {
                    throw illegalArg("Invalid tuple-number: $string")
                }
            }
        }

        /**
         * Deserialize a [TupleNumber] from the given parts array.
         *
         * The given array should contain, in order as **decimal string**:
         * - `storage-number` _(64-bit integer)_
         * - `map-number` _(32-bit integer)_
         * - `collection-number` _(32-bit integer)_
         * - `feature-number` _(64-bit integer)_
         * - `version` _(64-bit integer, the raw [Version.number] value)_
         * @param parts the string parts of the tuple-number.
         * @param offset the index in the given list where the `storage-number` is located, defaults to `0`.
         * @return the deserialized [TupleNumber].
         * @throws NakshaException with error [ILLEGAL_ARGUMENT][NakshaError.ILLEGAL_ARGUMENT] if the given parts are invalid.
         * @since 3.0
         */
        @JsStatic
        @JvmStatic
        fun fromParts(parts: List<String>, offset:Int = 0): TupleNumber {
            if (offset < 0 || (offset + TN_PARTS) > parts.size) {
                throw illegalArg("Invalid tuple-number: $parts")
            }
            val databaseNumber = parts[offset + STORAGE_NUMBER].toLong(10)
            val catalogNumber = parts[offset + MAP_NUMBER].toInt(10)
            val collectionNumber = parts[offset + COLLECTION_NUMBER].toInt(10)
            val featureNumber = parts[offset + FEATURE_NUMBER].toLong(10)
            val version = Version.fromString(parts[offset + VERSION]).number
            return TupleNumber(databaseNumber, catalogNumber, collectionNumber, featureNumber, version)
        }
    }
}

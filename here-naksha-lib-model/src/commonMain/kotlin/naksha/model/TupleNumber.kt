@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Binary
import naksha.base.Int64
import naksha.base.Platform
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_set_int32
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_set_int64
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.TupleNumberVariant.TupleNumberVariant_C.B64
import naksha.model.TupleNumberVariant.TupleNumberVariant_C.B128
import naksha.model.TupleNumberVariant.TupleNumberVariant_C.B160
import naksha.model.TupleNumberVariant.TupleNumberVariant_C.B192
import naksha.model.TupleNumberVariant.TupleNumberVariant_C.B256
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * The in-memory representation of the unique address of a [Tuple].
 *
 * The full qualified [Tuple] address is a 256-bit value _(32 byte)_, persisting out of the database-number, catalog-number, collection-number, feature-number, and version. Note that the lower two bits of version encode the [action][Action].
 *
 * The tuple-number is stringified either into URN:
 * ```
 * urn:naksha:tn:{database-number}:{catalog-number}:{collection-number}:{feature-number}:{version}
 * ```
 *
 * There are no two [tuple][Tuple] with the same [tuple-number][TupleNumber]; world-wide.
 * @since 3.0
 */
@JsExport
data class TupleNumber(
    /**
     * The database-number, uniquely identifies the storage where the tuple is stored.
     * @since 3.0
     */
    @JvmField val databaseNumber: Int64,

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
     * @see [Naksha.featureNumber]
     */
    @JvmField val featureNumber: Int64,

    /**
     * The version _(transaction)_ of which the [Tuple] is part of.
     * The lower 2 bits of [Version.number] encode the [Action].
     * @since 3.0
     * @see [Version.HEAD]
     */
    @JvmField val version: Int64,
) : Comparable<TupleNumber> {
    /**
     * The partition-number of the [Tuple], a value between `0` and `65536` _(exclusive)_.
     * @since 3.0
     */
    val partitionNumber: Int
        get() = featureNumber.toInt() and 0xffff

    /**
     * The [Action] applied to generate the [Tuple] referred by this [TupleNumber].
     * Decoded from the lower 2 bits of [version].
     * @since 3.0
     */
    val action: Action
        get() = Action.fromValue(version.toInt() and 3)

    /**
     * Calculates the distribution partition-index where this [Tuple] will be located.
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
        if (i64_diff > 1) return 1
        var i32_diff = catalogNumber - other.catalogNumber
        if (i32_diff < 0) return -1
        if (i32_diff > 1) return 1
        i32_diff = collectionNumber - other.collectionNumber
        if (i32_diff < 0) return -1
        if (i32_diff > 1) return 1
        i32_diff = partitionNumber - other.partitionNumber
        if (i32_diff < 0) return -1
        if (i32_diff > 1) return 1
        i64_diff = featureNumber - other.featureNumber
        if (i64_diff < 0) return -1
        if (i64_diff > 1) return 1
        i32_diff = version.compareTo(other.version)
        if (i32_diff < 0) return -1
        if (i32_diff > 1) return 1
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
     * Return the [TupleNumber] as string.
     * @return `{storage-number}:{map-number}:{collection-number}:{feature-number}:{version}`
     * @since 3.0.0
     * @see [fromString]
     * @see [toUrn]
     * @see [fromUrn]
     */
    override fun toString(): String {
        if (!this::_string.isInitialized) {
            _string = "$databaseNumber:$catalogNumber:$collectionNumber:$featureNumber:$version"
        }
        return _string
    }

    /**
     * Returns an alternative tuple-number, if this tuple-number causes a conflict in the auto-generated `feature-number`.
     *
     * This only happens when new features are created, and another feature, with a different **feature-id**, results in the same **feature-number**, because the lower 63-bit of their [MD5](https://en.wikipedia.org/wiki/MD5) hash collide. It deterministically calculates a new alternative **feature-number** and creates a new [TupleNumber] from it, that has just a new feature-number (no other changes). When this fails gain due to another conflict, the step can be repeated using this same function on the new, failing again, [TupleNumber] to generated yet another one, aso.
     *
     * ### Warning
     * The function returns deterministically always the same [TupleNumber], therefore, for more derivations it is necessary to call this function on the returned [TupleNumber] number, not again on the origin!
     *
     * - Throws [ILLEGAL_STATE] if the [featureNumber] is greater than `-1`, so not auto-generated.
     * @return an alternative[TupleNumber]
     * @since 3.0
     */
    fun resolveFeatureNumberConflict(): TupleNumber {
        val fn = this.featureNumber
        if (fn >= 0) throw NakshaException(ILLEGAL_STATE, "The feature-number is not auto-generated, failed to calculate alternative")
        val new_fn = Naksha.alternativeInt64(fn)
        return TupleNumber(databaseNumber, catalogNumber, collectionNumber, new_fn, version)
    }

    private var _urn: String? = null

    /**
     * Tests if this [TupleNumber] is [HEAD].
     * @return `true` if this is [HEAD]; `false` otherwise.
     * @since 3.0
     */
    fun isHead(): Boolean = this == HEAD

    /**
     * Convert this [TupleNumber] into a [URN](https://datatracker.ietf.org/doc/html/rfc8141), the exact format will be:
     * ```
     * urn:naksha:tn:{storage-number}:{map-number}:{collection-number}:{feature-number}:{version}
     * ```
     * @return the [URN](https://datatracker.ietf.org/doc/html/rfc8141) that describes this state world-wide uniquely.
     * @since 3.0
     */
    fun toUrn(): String {
        var urn = _urn
        if (urn == null) {
            urn = "urn:naksha:tn:${toString()}"
            _urn = urn
        }
        return urn
    }

    /**
     * Encode this [tuple-number][TupleNumber] into its binary representation, not storing the binary header upfront, encodes:
     * - `txn` _(aka [Version])_
     * @since 3.0
     */
    fun toB64(): ByteArray = toByteArray(B64)

    /**
     * Encode this [tuple-number][TupleNumber] into its binary representation, not storing the binary header upfront, encodes:
     * - `feature-number`
     * - `txn` _(aka [Version])_
     * @since 3.0
     */
    fun toB128(): ByteArray = toByteArray(B128)

    /**
     * Encode this [tuple-number][TupleNumber] into its binary representation, not storing the binary header upfront, encodes:
     * - `collection-number`
     * - `feature-number`
     * - `txn` _(aka [Version])_
     * @since 3.0
     */
    fun toB160(): ByteArray = toByteArray(B160)

    /**
     * Encode this [tuple-number][TupleNumber] into its binary representation, not storing the binary header upfront, encodes:
     * - `map-number`
     * - `collection-number`
     * - `feature-number`
     * - `txn` _(aka [Version])_
     * @since 3.0
     */
    fun toB192(): ByteArray = toByteArray(B192)

    /**
     * Encode this [tuple-number][TupleNumber] into its binary representation, not storing the binary header upfront, encodes:
     * - `storage-number`
     * - `map-number`
     * - `collection-number`
     * - `feature-number`
     * - `txn` _(aka [Version])_
     * @since 3.0
     */
    fun toB256(): ByteArray = toByteArray(B256)

    /**
     * Encode this [tuple-number][TupleNumber] into its binary representation, not storing the binary header upfront.
     *
     * This method is internally used to save space.
     *
     * @param variant the [TupleNumberVariant] to use for the encoding.
     * @return the binary encoded [tuple-number][TupleNumber].
     * @since 3.0
     * @see [naksha.model.request.query.MetaColumn.TUPLE_NUMBER]
     * @see [naksha.model.request.query.MetaColumn.BASE_TUPLE_NUMBER]
     */
    fun toByteArray(variant: TupleNumberVariant): ByteArray {
        val byteArray = ByteArray(variant.encodingBytes)
        val view = Platform.newDataView(byteArray)
        var offset = 0
        if (variant.encodeStorageNumber()) {
            dataview_set_int64(view, offset, databaseNumber)
            offset += 8
        }
        if (variant.encodeMapNumber()) {
            dataview_set_int32(view, offset, catalogNumber)
            offset += 4
        }
        if (variant.encodeCollectionNumber()) {
            dataview_set_int32(view, offset, collectionNumber)
            offset += 4
        }
        if (variant.encodeFeatureNumber()) {
            dataview_set_int64(view, offset, featureNumber)
            offset += 8
        }
        dataview_set_int64(view, offset, version)
        return byteArray
    }

    companion object TupleNumber_C {
        internal const val STORAGE_NUMBER = 0
        internal const val MAP_NUMBER = 1
        internal const val COLLECTION_NUMBER = 2
        internal const val FEATURE_NUMBER = 3
        internal const val VERSION = 4
        internal const val ALL_PARTS = 5

        internal const val URN = 0
        internal const val NAKSHA = 1
        internal const val TN = 2
        internal const val URN_STORAGE_NUMBER_OFFSET = 3
        internal const val URN_PARTS = ALL_PARTS + 3

        /**
         * Helper to create a new tuple-number based upon some values from an existing.
         *
         * @param tn the tuple-number from which to copy.
         * @param version if not `null`, overrides `tn.version`
         * @param featureNumber if not `null`, overrides `tn.featureNumber`
         * @param collectionNumber if not `null`, overrides `tn.collectionNumber`
         * @param mapNumber if not `null`, overrides `tn.mapNumber`
         * @param storageNumber if not `null`, overrides `tn.storageNumber`
         *
         */
        @JsStatic
        @JvmStatic
        @JvmOverloads
        fun copy(
            tn: TupleNumber,
            version: Int64? = null,
            featureNumber: Int64? = null,
            collectionNumber: Int? = null,
            mapNumber: Int? = null,
            storageNumber: Int64? = null,
        ) = TupleNumber(
            storageNumber ?: tn.databaseNumber,
            mapNumber ?: tn.catalogNumber,
            collectionNumber ?: tn.collectionNumber,
            featureNumber ?: tn.featureNumber,
            version ?: tn.version,
        )

        /**
         * The _HEAD_ [TupleNumber], to be used when a [tuple-number][TupleNumber] is not yet available.
         *
         * This happens for various reasons, for example when a [Tuple] is created in the client at runtime, and not yet persisted in any storage, therefore does not yet have a valid tuple-number.
         * @since 3.0
         */
        val HEAD = TupleNumber(Int64(0), 0, 0, Int64(0), Version.HEAD.number)

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
        ): TupleNumber = fromBinary(Binary(bytes, offset), variant, tn.databaseNumber, tn.catalogNumber, tn.collectionNumber, tn.featureNumber)

        fun fromB256(bytes: ByteArray)
                = fromByteArray(bytes, 0, B256)
        fun fromB192(bytes: ByteArray, storageNumber: Int64)
                = fromByteArray(bytes, 0, B192, storageNumber)
        fun fromB160(bytes: ByteArray, storageNumber: Int64, mapNumber: Int)
                = fromByteArray(bytes, 0, B160, storageNumber, mapNumber)
        fun fromB128(bytes: ByteArray, storageNumber: Int64, mapNumber: Int, collectionNumber: Int)
                = fromByteArray(bytes, 0, B128, storageNumber, mapNumber, collectionNumber)
        fun fromB64(bytes: ByteArray, storageNumber: Int64, mapNumber: Int, collectionNumber: Int, featureNumber: Int64)
                = fromByteArray(bytes, 0, B64, storageNumber, mapNumber, collectionNumber, featureNumber)

        /**
         * Restore a [TupleNumber] from a binary encoding.
         * @param bytes the binary to read.
         * @param offset the index of the first byte to read.
         * @param variant the variant to read, if omitted, the value is auto-detected by the byte-array size.
         * @param storageNumber if the binary does not encode the storage-number _(anything other than [B256])_, so variant is [B64], [B128], [B160], or [B192].
         * @param mapNumber if the binary does not encode the map-number, so variant is [B64], [B128], or [B160].
         * @param collectionNumber if the binary does not encode the collection-number, so variant is [B64] or [B128].
         * @param featureNumber if the binary does not encode the feature-number, so variant is [B64].
         */
        @JsStatic
        @JvmStatic
        @JvmOverloads
        fun fromByteArray(
            bytes: ByteArray,
            offset: Int = 0,
            variant: TupleNumberVariant = TupleNumberVariant.fromValue(bytes.size - offset),
            storageNumber: Int64? = null,
            mapNumber: Int? = null,
            collectionNumber: Int? = null,
            featureNumber: Int64? = null
        ): TupleNumber = fromBinary(Binary(bytes, offset), variant, storageNumber, mapNumber, collectionNumber, featureNumber)

        /**
         * Restore a [TupleNumber] from a binary encoding.
         *
         * @param binary the binary to read.
         * @param variant the variant to read.
         * @param storageNumber if the binary does not encode the storage-number _(anything other than [B256])_, so variant is [B64], [B128], [B160], or [B192].
         * @param mapNumber if the binary does not encode the map-number, so variant is [B64], [B128], or [B160].
         * @param collectionNumber if the binary does not encode the collection-number, so variant is [B64] or [B128].
         * @param featureNumber if the binary does not encode the feature-number, so variant is [B64].
         */
        @JsStatic
        @JvmStatic
        @JvmOverloads
        fun fromBinary(
            binary: Binary,
            variant: TupleNumberVariant,
            storageNumber: Int64? = null,
            mapNumber: Int? = null,
            collectionNumber: Int? = null,
            featureNumber: Int64? = null
        ): TupleNumber {
            val storage_num = if (variant.encodeStorageNumber()) binary.readInt64() else storageNumber
                ?: throw illegalArg("Missing storage-number for given tuple-number variant $variant")
            val map_num = if (variant.encodeMapNumber()) binary.readInt32() else mapNumber
                ?: throw illegalArg("Missing map-number for given tuple-number variant $variant")
            val col_num = if (variant.encodeCollectionNumber()) binary.readInt32() else collectionNumber
                ?: throw illegalArg("Missing collection-number for given tuple-number variant $variant")
            val f_num = if (variant.encodeFeatureNumber()) binary.readInt64() else featureNumber
                ?: throw illegalArg("Missing collection-number for given tuple-number variant $variant")
            val version = binary.readInt64()
            return TupleNumber(storage_num, map_num, col_num, f_num, version)
        }

        /**
         * Restore a [TupleNumber] from the stringified version.
         * @param string the string generated via [toString]
         * @return the deserialized [TupleNumber].
         */
        @JsStatic
        @JvmStatic
        fun fromString(string: String): TupleNumber {
            val parts = string.split(':')
            if (parts.size != ALL_PARTS) {
                throw NakshaException(ILLEGAL_ARGUMENT, "Invalid tuple-number string, require $ALL_PARTS parts: $string")
            }
            return fromParts(parts)
        }

        /**
         * Restore a [TupleNumber] from the given [URN](https://datatracker.ietf.org/doc/html/rfc8141), generated via [toUrn].
         * @param urn the [URN](https://datatracker.ietf.org/doc/html/rfc8141) from which to deserialize the [TupleNumber].
         * @return the deserialized [TupleNumber].
         * @since 3.0
         */
        @JsStatic
        @JvmStatic
        fun fromUrn(urn: String): TupleNumber {
            val parts = urn.split(':')
            if (parts.size != URN_PARTS
                || parts[URN] != "urn"
                || parts[NAKSHA] != "naksha"
                || parts[TN] != "tn") {
                throw NakshaException(ILLEGAL_ARGUMENT, "Invalid tuple-number URN: $urn")
            }
            return fromParts(parts, URN_STORAGE_NUMBER_OFFSET)
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
         * @since 3.0
         */
        @JsStatic
        @JvmStatic
        fun fromParts(parts: List<String>, offset:Int = 0): TupleNumber {
            if (offset < 0 || (offset + ALL_PARTS) > parts.size) {
                throw NakshaException(ILLEGAL_ARGUMENT, "Invalid tuple-number: $parts")
            }
            val storageNumber = Int64(parts[offset + STORAGE_NUMBER].toLong(10))
            val mapNumber = parts[offset + MAP_NUMBER].toInt(10)
            val colNumber = parts[offset + COLLECTION_NUMBER].toInt(10)
            val featureNumber = Int64(parts[offset + FEATURE_NUMBER].toLong(10))
            val version = Version.fromString(parts[offset + VERSION]).number
            return TupleNumber(storageNumber, mapNumber, colNumber, featureNumber, version)
        }
    }
}

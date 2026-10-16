@file:Suppress("OPT_IN_USAGE")

package naksha.base

import naksha.base.Action.Action_C.DELETE
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_set_int32
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_set_int64
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * The in-memory representation of the unique state address of a `Tuple`.
 *
 * The full qualified `Tuple` address is a 256-bit value _(32 byte)_, persisting out of the [database-number][databaseNumber], [catalog-number][catalogNumber], [collection-number][collectionNumber], [feature-number][featureNumber], and [version][version]. Note that the lower two bits of version encode the [action][Action].
 *
 * The tuple-number is stringified either into URN:
 * ```
 * urn:naksha:tn:{database-number}:{catalog-number}:{collection-number}:{feature-number}:{version}
 * ```
 *
 * There are no two tuple with the same [TupleNumber]; world-wide, unless they are really the same feature state.
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
     * The version _(transaction)_ of which the `Tuple` is part of. The lower 2 bits of [Version.number] encode the [Action].
     * @since 3.0
     * @see [Version.HEAD]
     */
    @JvmField val version: Long,
) : Comparable<TupleNumber> {

    /**
     * The [feature-number][featureNumber] as integer.
     *
     * If this identifier is for a catalog or collection, it is necessary to compare the lower 31-bit and sign-bit, that is for what this value exists.
     * @since 3.0
     */
    val intValue: Int
        get() = Id.numberToInt(featureNumber)

    /**
     * Create a new [TupleNumber] using [Id]'s.
     * @param databaseId the database [Id].
     * @param catalogId the catalog [Id].
     * @param collectionId the collection [Id].
     * @param featureId the feature [Id].
     * @param version the version.
     * @since 3.0
     */
    @JsName("NewTupleNumber")
    constructor(databaseId: Id, catalogId: Id, collectionId: Id, featureId: Id, version:Long)
        : this(databaseId.number, catalogId.intValue, collectionId.intValue, featureId.number, version)

    /**
     * The partition-number of the `Tuple`, a value between `0` and `256` _(exclusive)_.
     * @since 3.0
     */
    val partitionNumber: Int
        get() = Id.partitionNumber(featureNumber)

    /**
     * The [Action] applied to generate the `Tuple` referred by this [TupleNumber].
     * Decoded from the lower 2 bits of [version].
     * @since 3.0
     */
    val action: Action
        get() = Action.fromValue(version.toInt() and 3)

    /**
     * Tests if the `Tuple` is a tombstone, so a deleted state.
     * @since 3.0
     */
    @get:JvmName("isTombstone")
    val isTombstone: Boolean
        get() = action == DELETE

    /**
     * Calculates the distribution partition-index where this `Tuple` will be located.
     *
     * If the given partitions are less than `2`, the method always returns `-1`.
     * @param partitions the number of partitions
     * @return the partition-index, a value between `0` and `partitions - 1` _(maximal 255)_; or `-1` if there are no distribution partitions.
     * @since 3.0
     */
    fun partitionIndex(partitions: Int): Int = if (partitions < 2) -1 else partitionNumber % partitions

    override fun hashCode(): Int = version.hashCode()

    /**
     * Compares this [TupleNumber] with the specified `other` [TupleNumber].
     *
     * Returns zero if this [TupleNumber] is equal to the specified `other` [TupleNumber], a negative number if it's less than `other`, or a positive number if it's greater than `other`.
     *
     * _This is not a simple numeric compare, it considers certain special cases and does a bunch of bit mangling to ensure that special catalogs and collections are sorted first, and that features are ordered by partitions. This method works for all object types with the same reliability!_
     *
     * ## Details
     * This compares first [databases-number][TupleNumber.databaseNumber], then [catalog-number][TupleNumber.catalogNumber], then [collection-number][TupleNumber.collectionNumber], then [partition-number][TupleNumber.partitionNumber], then [action][TupleNumber.action], then [feature-number][TupleNumber.featureNumber], and finally [version][TupleNumber.version]. Beware, this method compares the raw partition-number being a value between 0 and 255, it does not care if partitions exist or not. It ensures that all operations are executed in partition order.
     *
     * When comparing the [feature-number][TupleNumber.featureNumber] the compare is done in a special way as described by [Id.compareFeatureNumber].
     *
     * ## Important
     * This compare is important to order operations. All operations must be ordered consistent cross-platform to avoid deadlocks.
     *
     * **Only when all clients modify data in the same deterministic order it will work!**
     *
     * If the clients use different ordering, they will run in deadlocks. For example, assume two client want to modify the same objects _A_, _B_, and _C_. Say client `X` tries to execute this in order _A_, _B_, _C_ and client `Y` tries in order _C_, _B_, _A_. Now assume the clients run in parallel, and are executed in the following order _(they run at same speed, really parallel)_:
     * - Client `X` acquires lock on _A_.
     * - Client `Y` acquires lock on _C_.
     * - Client `X` acquires lock on _B_.
     * - Client `Y` tries lock on _B_.
     * - Client `Y` will be parked, because _B_ is locked by `X`, it will wait for the lock.
     * - Client `X` tries lock on _C_.
     * - Client `X` will be parked, because _C_ is locked by `Y`, it will wait for the lock.
     *
     * At this point both clients are waiting for each other to finish, this is called a deadlock. This can't happen when both clients use the same order. So, if both clients use the order _A_, _B_, _C_ the one that holds the lock on _A_ will be able to finish execution before all other clients that need _A_ as well. Beware, the above was a very simple example, it gets much more complicated with three or more parallel clients. However, all these issues are solved by using a deterministic order.
     *
     * @param other the other [TupleNumber] to compare this against.
     * @return zero if this [TupleNumber] is equal to the specified `other`, a negative number if this is less than `other`, or a positive number if this is greater than `other`.
     * @see Id.compareFeatureNumber
     */
    override fun compareTo(other: TupleNumber): Int {
        val diff64 = databaseNumber - other.databaseNumber
        if (diff64 < 0) return -1
        if (diff64 > 0) return 1
        var diff = catalogNumber - other.catalogNumber
        if (diff < 0) return -1
        if (diff > 0) return 1
        diff = collectionNumber - other.collectionNumber
        if (diff < 0) return -1
        if (diff > 0) return 1
        diff = partitionNumber - other.partitionNumber
        if (diff < 0) return -1
        if (diff > 0) return 1
        val this_action = this.action.intValue
        val other_action = other.action.intValue
        if (this_action != other_action) {
            // We order DELETE (2) first (note: we know actions are not equal!).
            if (this_action == DELETE.intValue) return -1
            if (other_action == DELETE.intValue) return 1
            // Then CREATE (0), then UPDATE (1), then VERSION (3)
            diff = this_action - other_action
            return if (diff < 0) -1 else 1
        }
        diff = Id.compareFeatureNumber(featureNumber, other.featureNumber)
        if (diff < 0) return -1
        if (diff > 0) return 1
        diff = version.compareTo(other.version)
        if (diff < 0) return -1
        if (diff > 0) return 1
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

    private var _urn: String? = null

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
        const val ALL_PARTS = 5

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
            version: Long? = null,
            featureNumber: Long? = null,
            collectionNumber: Int? = null,
            mapNumber: Int? = null,
            storageNumber: Long? = null,
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
         * This happens for various reasons, for example when a `Tuple` is created in the client at runtime, and not yet persisted in any storage, therefore does not yet have a valid tuple-number.
         * @since 3.0
         */
        val HEAD = TupleNumber(0, 0, 0, 0, Version.HEAD.number)

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
                = fromByteArray(bytes, 0, TupleNumberVariant.B256)
        fun fromB192(bytes: ByteArray, storageNumber: Long)
                = fromByteArray(bytes, 0, TupleNumberVariant.B192, storageNumber)
        fun fromB160(bytes: ByteArray, storageNumber: Long, mapNumber: Int)
                = fromByteArray(bytes, 0, TupleNumberVariant.B160, storageNumber, mapNumber)
        fun fromB128(bytes: ByteArray, storageNumber: Long, mapNumber: Int, collectionNumber: Int)
                = fromByteArray(bytes, 0, TupleNumberVariant.B128, storageNumber, mapNumber, collectionNumber)
        fun fromB64(bytes: ByteArray, storageNumber: Long, mapNumber: Int, collectionNumber: Int, featureNumber: Long)
                = fromByteArray(bytes, 0, TupleNumberVariant.B64, storageNumber, mapNumber, collectionNumber, featureNumber)

        /**
         * Restore a [TupleNumber] from a binary encoding.
         * @param bytes the binary to read.
         * @param offset the index of the first byte to read.
         * @param variant the variant to read, if omitted, the value is auto-detected by the byte-array size.
         * @param storageNumber if the binary does not encode the storage-number _(anything other than [TupleNumberVariant.TupleNumberVariant_C.B256])_, so variant is [TupleNumberVariant.TupleNumberVariant_C.B64], [TupleNumberVariant.TupleNumberVariant_C.B128], [TupleNumberVariant.TupleNumberVariant_C.B160], or [TupleNumberVariant.TupleNumberVariant_C.B192].
         * @param mapNumber if the binary does not encode the map-number, so variant is [TupleNumberVariant.TupleNumberVariant_C.B64], [TupleNumberVariant.TupleNumberVariant_C.B128], or [TupleNumberVariant.TupleNumberVariant_C.B160].
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
            storageNumber: Long? = null,
            mapNumber: Int? = null,
            collectionNumber: Int? = null,
            featureNumber: Long? = null
        ): TupleNumber = fromBinary(Binary(bytes, offset), variant, storageNumber, mapNumber, collectionNumber, featureNumber)

        /**
         * Restore a [TupleNumber] from a binary encoding.
         *
         * @param binary the binary to read.
         * @param variant the variant to read.
         * @param storageNumber if the binary does not encode the storage-number _(anything other than [TupleNumberVariant.TupleNumberVariant_C.B256])_, so variant is [TupleNumberVariant.TupleNumberVariant_C.B64], [TupleNumberVariant.TupleNumberVariant_C.B128], [TupleNumberVariant.TupleNumberVariant_C.B160], or [TupleNumberVariant.TupleNumberVariant_C.B192].
         * @param mapNumber if the binary does not encode the map-number, so variant is [TupleNumberVariant.TupleNumberVariant_C.B64], [TupleNumberVariant.TupleNumberVariant_C.B128], or [TupleNumberVariant.TupleNumberVariant_C.B160].
         * @param collectionNumber if the binary does not encode the collection-number, so variant is [TupleNumberVariant.TupleNumberVariant_C.B64] or [TupleNumberVariant.TupleNumberVariant_C.B128].
         * @param featureNumber if the binary does not encode the feature-number, so variant is [TupleNumberVariant.TupleNumberVariant_C.B64].
         */
        @JsStatic
        @JvmStatic
        @JvmOverloads
        fun fromBinary(
            binary: Binary,
            variant: TupleNumberVariant,
            storageNumber: Long? = null,
            mapNumber: Int? = null,
            collectionNumber: Int? = null,
            featureNumber: Long? = null
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
                throw IllegalArgumentException("Invalid tuple-number string, require $ALL_PARTS parts: $string")
            }
            return fromParts(parts)
        }

        /**
         * Like [fromString], but also accepts a full GUID URN (`urn:naksha:guid:...`) and extracts the embedded
         * tuple-number — tuple-number members are often stored as the feature's GUID (e.g. the XYZ `uuid`).
         * @param string the bare tuple-number string or a GUID URN.
         * @return the deserialized [TupleNumber].
         * @since 3.0
         */
        @JsStatic
        @JvmStatic
        fun fromStringOrGuid(string: String): TupleNumber =
            if (string.startsWith("urn:naksha:guid:")) Guid.fromString(string).tupleNumber
            else fromString(string)

        /**
         * Restore a [TupleNumber] from the given [URN](https://datatracker.ietf.org/doc/html/rfc8141), generated via [toUrn].
         * @param urn the [URN](https://datatracker.ietf.org/doc/html/rfc8141) from which to deserialize the [TupleNumber].
         * @return the deserialized [TupleNumber].
         * @throws NakshaException with error [ILLEGAL_ARGUMENT][NakshaError.ILLEGAL_ARGUMENT] if the given string is invalid.
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
                throw illegalArg("Invalid tuple-number URN: $urn")
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
         * @throws NakshaException with error [ILLEGAL_ARGUMENT][NakshaError.ILLEGAL_ARGUMENT] if the given parts are invalid.
         * @since 3.0
         */
        @JsStatic
        @JvmStatic
        fun fromParts(parts: List<String>, offset:Int = 0): TupleNumber {
            if (offset < 0 || (offset + ALL_PARTS) > parts.size) {
                throw illegalArg("Invalid tuple-number: $parts")
            }
            val storageNumber = parts[offset + STORAGE_NUMBER].toLong(10)
            val mapNumber = parts[offset + MAP_NUMBER].toInt(10)
            val colNumber = parts[offset + COLLECTION_NUMBER].toInt(10)
            val featureNumber = parts[offset + FEATURE_NUMBER].toLong(10)
            val version = Version.fromString(parts[offset + VERSION]).number
            return TupleNumber(storageNumber, mapNumber, colNumber, featureNumber, version)
        }
    }
}

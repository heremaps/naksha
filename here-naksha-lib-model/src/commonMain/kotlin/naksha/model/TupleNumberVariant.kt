@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.JsEnum
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass

/**
 * The possible [TupleNumber] binary encoding.
 * @since 3.0
 */
@JsExport
class TupleNumberVariant internal constructor() : JsEnum() {
    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = TupleNumberVariant::class

    override fun initClass() {}

    companion object TupleNumberVariant_C {
        /**
         * The 256-bit _(32 byte)_ encoding, variant `0`.
         * - `storage-number`
         * - `map-number`
         * - `collection-number`
         * - `feature-number`
         * - `txn` _(aka [Version])_
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val B256 = def(TupleNumberVariant::class, 0) { self ->
            self.subType = 0
            self.encodingBytes = 32
            self.sharedBytes = 0
            self.bits = self.encodingBytes * 8
            check(self.encodingBytes + self.sharedBytes == 32)
        }

        /**
         * The 192-bit _(24 byte)_ encoding, variant `1`.
         * - `map-number`
         * - `collection-number`
         * - `feature-number`
         * - `txn` _(aka [Version])_
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val B192 = def(TupleNumberVariant::class, 1) { self ->
            self.subType = 1
            self.encodingBytes = 24
            self.sharedBytes = 8
            self.bits = self.encodingBytes * 8
            check(self.encodingBytes + self.sharedBytes == 32)
        }

        /**
         * The 160-bit _(20 byte)_ encoding, variant `2`.
         * - `collection-number`
         * - `feature-number`
         * - `txn` _(aka [Version])_
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val B160 = def(TupleNumberVariant::class, 2) { self ->
            self.subType = 2
            self.encodingBytes = 20
            self.sharedBytes = 12
            self.bits = self.encodingBytes * 8
            check(self.encodingBytes + self.sharedBytes == 32)
        }

        /**
         * The 128-bit _(16 byte)_ encoding, variant `3`.
         * - `feature-number`
         * - `txn` _(aka [Version])_
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val B128 = def(TupleNumberVariant::class, 3) { self ->
            self.subType = 3
            self.encodingBytes = 16
            self.sharedBytes = 16
            self.bits = self.encodingBytes * 8
            check(self.encodingBytes + self.sharedBytes == 32)
        }

        /**
         * The 64-bit _(8 byte)_ encoding, variant `4`.
         * - `txn` _(aka [Version])_
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val B64 = def(TupleNumberVariant::class, 4) { self ->
            self.subType = 4
            self.encodingBytes = 8
            self.sharedBytes = 24
            self.bits = self.encodingBytes * 8
            check(self.encodingBytes + self.sharedBytes == 32)
        }

        /**
         * Placeholder for undefined variants.
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val UNDEFINED = def(TupleNumberVariant::class, "undefined")

        private val FROM_STRING = mapOf(
            Pair(B256.subType.toString(), B256),
            Pair(B256.encodingBytes.toString(), B256),
            Pair(B256.bits.toString(), B256),

            Pair(B192.subType.toString(), B192),
            Pair(B192.encodingBytes.toString(), B192),
            Pair(B192.bits.toString(), B192),

            Pair(B160.subType.toString(), B160),
            Pair(B160.encodingBytes.toString(), B160),
            Pair(B160.bits.toString(), B160),

            Pair(B128.subType.toString(), B128),
            Pair(B128.encodingBytes.toString(), B128),
            Pair(B128.bits.toString(), B128),

            Pair(B64.subType.toString(), B64),
            Pair(B64.bits.toString(), B64),
            Pair(B64.encodingBytes.toString(), B64),
        )

        private val FROM_VALUE = mapOf(
            Pair(B256.subType, B256),
            Pair(B256.encodingBytes, B256),
            Pair(B256.bits, B256),

            Pair(B192.subType, B192),
            Pair(B192.encodingBytes, B192),
            Pair(B192.bits, B192),

            Pair(B160.subType, B160),
            Pair(B160.encodingBytes, B160),
            Pair(B160.bits, B160),

            Pair(B128.subType, B128),
            Pair(B128.encodingBytes, B128),
            Pair(B128.bits, B128),

            Pair(B64.subType, B64),
            Pair(B64.bits, B64),
            Pair(B64.encodingBytes, B64),
        )

        /**
         * Helper to parse a string into an [Action].
         */
        @JsStatic
        @JvmStatic
        fun fromString(s: String): TupleNumberVariant = FROM_STRING[s] ?: UNDEFINED

        /**
         * Helper to parse a string into an [Action].
         */
        @JsStatic
        @JvmStatic
        fun fromValue(value: Int): TupleNumberVariant = FROM_VALUE[value] ?: UNDEFINED
    }

    /**
     * The subtype, when encoded in a [TupleNumberBinaryArray].
     * @since 3.0
     */
    var subType: Int = -1
        private set

    /**
     * The number of bytes in which this type is encoded.
     * @since 3.0
     */
    var encodingBytes: Int = -1
        private set

    /**
     * The number of bytes that are shared in the header of a tuple-number-array for tuple-number's of this variant.
     * @since 3.0
     * @see [BinaryUtil.writeSimpleHeader]
     * @see [sharedStorageNumber]
     * @see [encodeStorageNumber]
     * @see [sharedMapNumber]
     * @see [encodeMapNumber]
     * @see [sharedCollectionNumber]
     * @see [encodeCollectionNumber]
     * @see [sharedFeatureNumber]
     * @see [encodeFeatureNumber]
     */
    var sharedBytes: Int = -1
        private set

    /**
     * The number of bits in which this type is encoded.
     * @since 3.0
     */
    var bits: Int = -1
        private set

    /**
     * Tests if this variant encodes the `storage-number` in the header.
     */
    fun sharedStorageNumber(): Boolean = !encodeStorageNumber()

    /**
     * Tests if this variant encodes the `storage-number`.
     */
    fun encodeStorageNumber(): Boolean = subType <= B256.subType

    /**
     * Tests if this variant encodes the `map-number` in the header.
     */
    fun sharedMapNumber(): Boolean = !encodeMapNumber()

    /**
     * Tests if this variant encodes the `map-number`.
     */
    fun encodeMapNumber(): Boolean = subType <= B192.subType

    /**
     * Tests if this variant encodes the `collection-number` in the header.
     */
    fun sharedCollectionNumber(): Boolean = !encodeCollectionNumber()

    /**
     * Tests if this variant encodes the `collection-number`.
     */
    fun encodeCollectionNumber(): Boolean = subType <= B160.subType

    /**
     * Tests if this variant encodes the `feature-number` in the header.
     */
    fun sharedFeatureNumber(): Boolean = !encodeFeatureNumber()

    /**
     * Tests if this variant encodes the `feature-number`.
     */
    fun encodeFeatureNumber(): Boolean = subType <= B128.subType
}

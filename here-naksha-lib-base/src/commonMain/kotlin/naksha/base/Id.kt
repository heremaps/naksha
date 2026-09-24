@file:OptIn(ExperimentalJsExport::class, ExperimentalJsStatic::class)

package naksha.base

import naksha.base.Platform.PlatformCompanion.md5
import naksha.base.PlatformUtil.PlatformUtilCompanion.randomAtoZ
import naksha.base.PlatformUtil.PlatformUtilCompanion.randomString
import kotlin.js.ExperimentalJsExport
import kotlin.js.ExperimentalJsStatic
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic

/**
 * A wrapper for Naksha identifiers.
 *
 * Invoking the constructor without arguments will create a new random identifier.
 *
 * All identifiers in Naksha have a text and a numeric representation. The textual representation is used for serialization and can be used to synchronize external storages, as long as this does not cause collisions.
 *
 * **The numeric representation of an identifier is the primary key in Naksha.**
 *
 * If the text is a positive 64-bit integer in decimal notation, like `"1234"`, then the numeric representation is the same parsed into a positive 64-bit integer. Otherwise, an [MD5](https://en.wikipedia.org/wiki/MD5) hash is calculated above the text resulting in a 16-byte hash. The lower 64-bit _(at offset 8 in the returned byte-array)_ are read in [Big-Endian Byte-Order](https://en.wikipedia.org/wiki/Endianness), the sign bit is set _(bit #63)_, so that a negative 64-bit integer is made and used as feature-number.
 *
 * It is expensive when an application has to perform these calculations multiple times per object. To avoid this the [Id] class has been created. It can be used to carry around the texual and the numeric representation together, avoiding multi-hashing.
 *
 * The [Id] class offers a static helper method to calculate the number from the `id` _([textToNumber])_. The method will detect if the `id` is a positive 64-bit integer, if that is the case, it will convert this string into the corresponding 64-bit integer, and return it.
 *
 * Otherwise, it uses the [MD5](https://en.wikipedia.org/wiki/MD5) hash above the `id` and returns the lower 64-bit as number, with the highest bit _(sign-bit)_ set, so it gets turned into a negative 64-bit integer.
 *
 * This reserves all positive numbers for manually managed identifiers, which is compatible to what `Map-Hub` originally did and what is needed in many cases, it as well reduces the amount of data that has to be stored for such identifiers, which is especially beneficial for small objects.
 *
 * ### Collision handling
 * Considering the [birthday paradox](https://betterexplained.com/articles/understanding-the-birthday-paradox/) we can assume that for a maximum of 2^40 features in a collection, there will be around 65,000 collisions, when using 2^32 features _(4 billion)_ we should see around two collisions, while for less than one billion features we will not encounter any collision _(or, it is highly unlikely)_. By design we expect that no single collection has more than 250 million features.
 *
 * As collisions in feature-number generation using [MD5](https://en.wikipedia.org/wiki/MD5) hashing is not avoidable, we need a strategy when it happens, even while it should be very unlikely. Deligating collision handling into the storage increases the implementation effort drastically and has tons of side effects. Therefore, should there be a collision in a certain collection, again, which is highly unlikely, the client has to care about this. The storage only has to raise a [NakshaException] with error being [ID_COLLISION][NakshaError.ID_COLLISION]. The recommended solution is to either split the data into multiple collections or to generate a new identifier.
 *
 * ### Catalogs and Collections
 * Due to the way how the state identifiers of objects are addressed, the numeric representation of catalogs and collections is truncated to 32-bit. This is done by clearing the top 33-bit, then copy back the sign bit, so that negative integers stay negative.
 *
 * This means that hash collisions in catalogs and collections are much more likely. However, the expected number of catalogs per database is around 1000; and the expected maximum of collections per catalog is as well around 1000. For these numbers there should not be any collision; at least it is unlikely _(around 0.0233%)_. Beware that using this maximum means to handle 1 million containers in a single database; that should leave enough room.
 *
 * ### Note
 * Generally, the estimated number of collisions is calculated as `n^2 / 2N` with `n` being the number of features and `N` being the entropy, so the maximum amount of numbers available _(so here 2^63)_. The collision possibility can be estimated via `1 - e^( -(n^2 / 2N) )`, for example, for 1 billion features it will be `1 - e^( -(2^60 / 2^64) )`, which results in around 6 percent, for 4 billion features it grows to `1 - e^( -(2^64 / 2^64) )` to around 63.2 percent, reaching 99.99% for around 147 billion features _(there is expected to be at least one collision)_.
 *
 * **Beware, just because a collision is unlikely, does not mean there will be none!**
 * @see text
 * @see number
 * @see intValue
 * @see partitionNumber
 * @see partitionIndex
 */
@JsExport
class Id private constructor(
    private var _number: Long,
    private var _text: String?,
) : Comparable<Id?>, CharSequence {

    /**
     * Creates a numeric identifier, requires the given `number` to be a positive integer.
     * @param number the positive numeric identifier.
     * @since 3.0
     * @see intValue
     * @see text
     * @see fromValue
     */
    @JsName("newNumericId")
    constructor(number: Long) : this(requirePositiveNumber(number), if (number == 0L) ZERO else null)

    /**
     * Create an identifier based upon the given textual representation.
     * @param text the textual identifier.
     * @since 3.0
     */
    @JsName("newStringId")
    constructor(text: String) : this(0L, if (text == ZERO) ZERO else text)

    /**
     * Create a new random identifier.
     * @since 3.0
     */
    @JsName("newRandomId")
    constructor() : this(0L, randomString())

    /**
     * The numeric representation of the `id`, when not given, automatically calculated from the [text].
     *
     * ### Catalogs and Collections
     * For catalogs and collections this number will be the full number, so the 64-bit value. However, only the lower 32-bit, as returned by [intValue], are significant. This means, internally the storages will trim the number down to 32-bit and when two catalogs or collections have the same lower 32-bit, they are treated as being the same. This is done, because only the 32-bit value is encoded in [tuple-numbers][TupleNumber]. In other words, the amount of collisions for catalogs and collections is much higher. However, it is not expected to have millions of catalogs in the same database, or millions of collections in the same catalog, therefore this is a fair tradeoff between encoding size of [TupleNumber] and collision resistance.
     * @since 3.0
     * @see intValue
     * @see text
     * @see fromValue
     */
    @get:JvmName("number")
    val number: Long
        get() {
            var number = this._number
            if (number == 0L) {
                val text = this._text ?: ZERO
                @Suppress("StringReferentialEquality")
                if (text !== ZERO) {
                    // If the text is actually "0", replace it with ZERO, so we do not try to parse it again.
                    if (text == ZERO) _text = ZERO
                    else {
                        number = textToNumber(text)
                        this._number = number
                    }
                }
            }
            return number
        }

    /**
     * The textual representation of the `id`, when not given a random identifier is generated.
     * @since 3.0
     * @see intValue
     * @see number
     * @see fromValue
     */
    @get:JvmName("text")
    val text: String
        get() {
            var text = this._text
            if (text == null) {
                text = numberToText(_number)
                this._text = text
            }
            return text
        }

    /**
     * Returns the [number] as 32-bit integer, needed for catalogs and collections.
     *
     * The method will keep the lower 31-bit of the [number] intact and copy the sign-bit to them, so that the resulting 32-bit value will match the lower 31-bit plus the sign of [number]. This guarantees that negative numbers stay negative, and positive numbers stay positive. In case of a negative number, it effectively returns the 32-bit at offset `12` of the [MD5](https://en.wikipedia.org/wiki/MD5) hash above the `id` string, with a set sign-bit.
     * @since 3.0
     * @see Id.numberToInt
     * @see Id.textToInt
     */
    @get:JvmName("intValue")
    val intValue: Int
        get() = numberToInt(number)

    /**
     * Tests if the identifier is a pure numeric identifier, so the [text] is just the stringified number _(only for positive numbers)_.
     * @since 3.0
     */
    @get:JvmName("isNumeric")
    val isNumeric: Boolean
        get() = number >= 0

    /**
     * The number of the partition in which this identifier will be located.
     *
     * Beware that the final partition index is dependent on the number of total partitions.
     * @see partitionIndex
     */
    @JvmField
    val partitionNumber: Int = number.toInt() and 0xffff

    /**
     * Returns the real partition index of this identifier.
     * @param partitions the total number of partitions as specified in `NakshaCollection`.
     * @return the real partition index of this identifier for the given amount of partitions.
     * @see partitionNumber
     */
    fun partitionIndex(partitions: Int): Int = partitionNumber % partitions

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is Id) return false
        return number == other.number
    }
    override fun hashCode(): Int = intValue
    override fun toString(): String = text
    override fun compareTo(other: Id?): Int {
        // We order `null` at the end of lists/arrays.
        if (other == null) return -1
        if (this === other) return 0
        return number.compareTo(other.number)
    }

    // ---------------------------------------------< CharSequence >------------------------------------------------------
    override val length: Int
        get() = text.length
    override fun get(index: Int): Char = text[index]
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = text.subSequence(startIndex, endIndex)
    // -------------------------------------------------------------------------------------------------------------------

    companion object IdCompanion {
        /** Text of the administration catalog identifier (`naksha~admin`). */
        const val ADMIN_CATALOG_TEXT = "${INTERNAL_PREFIX}admin"

        /** Quoted text of the administration catalog identifier (`"naksha~admin"`). */
        const val ADMIN_CATALOG_QUOTED = "\"${INTERNAL_PREFIX}admin\""

        /** Number of the administration catalog (fixed to `-5047830975677239995`). */
        const val ADMIN_CATALOG_NUMBER: Long = -5047830975677239995L

        /** Int-Number of the administration catalog (fixed to `-527733435`). */
        const val ADMIN_CATALOG_INT: Int = -527733435

        /** Text of the collections-collection identifier (`naksha~collections`). */
        const val COLLECTIONS_COL_TEXT = "${INTERNAL_PREFIX}collections"

        /** Quoted text of the collections-collection identifier (`"naksha~collections"`). */
        const val COLLECTIONS_COL_QUOTED = "\"${INTERNAL_PREFIX}collections\""

        /** Number of the collections-collection (fixed to `-784851911399779908`). */
        const val COLLECTIONS_COL_NUMBER: Long = -784851911399779908L

        /** Int-Number of the collections-collection (fixed to `-876949060`). */
        const val COLLECTIONS_COL_INT: Int = -876949060

        /** Text of the transactions-collection identifier (`naksha~transactions"`). */
        const val TRANSACTIONS_COL_TEXT = "${INTERNAL_PREFIX}transactions"

        /** Quoted text of the transactions-collection identifier (`"naksha~transactions"`). */
        const val TRANSACTIONS_COL_QUOTED = "\"${INTERNAL_PREFIX}transactions\""

        /** Number of the transactions-collection (fixed to `-4460574983858345304`). */
        const val TRANSACTIONS_COL_NUMBER: Long = -4460574983858345304L

        /** Int-Number of the transactions-collection (fixed to `-249484632`). */
        const val TRANSACTIONS_COL_INT: Int = -249484632

        /** Text of the catalogs-collection identifier (`naksha~catalogs`). */
        const val CATALOGS_COL_TEXT = "${INTERNAL_PREFIX}catalogs"

        /** Quoted text of the catalogs-collection identifier (`"naksha~catalogs"`). */
        const val CATALOGS_COL_QUOTED = "\"${INTERNAL_PREFIX}catalogs\""

        /** Number of the catalogs-collection (fixed to `-8430948877866261206`). */
        const val CATALOGS_COL_NUMBER = -8430948877866261206L

        /** Int-Number of the catalogs-collection (fixed to `-1488083670`). */
        const val CATALOGS_COL_INT: Int = -1488083670

        /** Text of the books-collection identifier (`naksha~books`). */
        const val BOOKS_COL_TEXT = "${INTERNAL_PREFIX}books"

        /** Quoted text of the books-collection identifier (`"naksha~books"`). */
        const val BOOKS_COL_QUOTED = "\'${INTERNAL_PREFIX}books\'"

        /** Number of the books-collection (fixed to `-9124739062881139952`). */
        const val BOOKS_COL_NUMBER: Long = -9124739062881139952L

        /** Int-Number of the books-collection (fixed to `-791231728`). */
        const val BOOKS_COL_INT: Int = -791231728

        /**
         * Private marker string object.
         */
        private val ZERO = charArrayOf('0').concatToString()

        /**
         * An immutable map between the identifier text and the number.
         * @since 3.0
         */
        @JvmStatic
        private val textToNumberMap = mapOf(
            Pair(ADMIN_CATALOG_TEXT, ADMIN_CATALOG_NUMBER),
            Pair(COLLECTIONS_COL_TEXT, COLLECTIONS_COL_NUMBER),
            Pair(TRANSACTIONS_COL_TEXT, TRANSACTIONS_COL_NUMBER),
            Pair(CATALOGS_COL_TEXT, CATALOGS_COL_NUMBER),
            Pair(BOOKS_COL_TEXT, BOOKS_COL_NUMBER),
        )

        /**
         * An immutable map between the number and the text.
         * @since 3.0
         */
        @JvmStatic
        private val numberToTextMap = mapOf(
            Pair(ADMIN_CATALOG_NUMBER, ADMIN_CATALOG_TEXT),
            Pair(COLLECTIONS_COL_NUMBER, COLLECTIONS_COL_TEXT),
            Pair(TRANSACTIONS_COL_NUMBER, TRANSACTIONS_COL_TEXT),
            Pair(CATALOGS_COL_NUMBER, CATALOGS_COL_TEXT),
            Pair(BOOKS_COL_NUMBER, BOOKS_COL_TEXT),
        )

        /**
         * `0x8000_0000_0000_0000`, should be `-9223372036854775808`, but this does not work in Kotlin, only `-9223372036854775807 -1`?
         * - See [programmer calculator](https://devtools.calckit.io/programmer-calculator)
         */
        @JvmStatic
        internal val INT64_SIGN_BIT = Long.MIN_VALUE

        /** `^[1-9][0-9]{0,18}$` — 63-bit unsigned integer in text form. */
        @JvmStatic
        private val IS_63BIT_UNSIGNED = Regex("^[1-9][0-9]{0,18}$")

        /**
         * A method to convert a numeric identifier into a textual.
         *
         * @param number the number for which to return the string version.
         * @return the number as string.
         * @since 3.0
         * @throws NakshaException with [NakshaError.ILLEGAL_ARGUMENT] if the given `number` can't be converted into a string.
         */
        @JvmStatic
        @JsStatic
        fun numberToText(number: Long): String {
            if (number >= 0L) return number.toString()
            return numberToTextMap[number] ?: throw illegalArg("The text value of the given number is unknown: $number")
        }

        /**
         * Calculate the [MD5](https://en.wikipedia.org/wiki/MD5) hash above the given text and returns the 64-bit Big-Endian value at offset 8 of the generated hash, setting the sign-bit, so that the returned value is always negative.
         * @param text the text.
         * @return the negative 64-bit hash above the given identifier.
         * @since 3.0
         * @see textToNumber
         */
        @JvmStatic
        @JsStatic
        fun hashText(text: String): Long = md5(text).getInt64Be(8) or INT64_SIGN_BIT

        /**
         * Calculate the number from a textual identifier. Results in a positive number, if the `id` is a 63-bit unsigned integer literal, otherwise a negative number is returned as [MD5](https://en.wikipedia.org/wiki/MD5) hash above the `id`.
         * @param id the textual identifier.
         * @return the numeric identifier calculated from the textual one.
         * @since 3.0
         */
        @JvmStatic
        @JsStatic
        fun textToNumber(id: String): Long {
            if (id == "0" || IS_63BIT_UNSIGNED.matches(id)) {
                try { return id.toLong(10) } catch (_: Exception) {}
            }
            return textToNumberMap[id] ?: hashText(id)
        }

        /**
         * Calculate the 32-bit number from a textual identifier.
         *
         * This call can be replaced with:
         * ```kotlin
         * numberToInt(textToNumber(id))
         * ```
         * @param id the textual identifier.
         * @return the numeric identifier calculated from the textual one.
         * @since 3.0
         * @see textToNumber
         * @see numberToInt
         */
        @JvmStatic
        @JsStatic
        fun textToInt(id: String): Int = numberToInt(textToNumber(id))

        /**
         * Converts the given identifier into a 64-bit positive feature-number, if the given `id` is a valid positive integer in the supported range.
         *
         * This method is faster than [textToNumber] if the feature-number is only needed, when it is positive. Internally used when detecting numeric identifies in query building. This method does not apply an [MD5](https://en.wikipedia.org/wiki/MD5) hash.
         * @param id the feature-id as string.
         * @return the feature-id as positive number, when being a positive number; `-1` otherwise.
         * @since 3.0
         */
        @JsStatic
        @JvmStatic
        fun textToPositiveNumber(id: String): Long {
            if (id == "0" || IS_63BIT_UNSIGNED.matches(id)) {
                try { return id.toLong(10) } catch (_: Exception) {}
            }
            return -1L
        }

        /**
         * Returns the given 64-bit feature-number as 32-bit integer, needed for catalogs and collections.
         *
         * The method will keep the lower 31-bit of the `featureNumber` intact and copy the sign-bit to them, so that the resulting 32-bit value will match the lower 31-bit plus the sign of `featureNumber`. This guarantees that negative numbers stay negative, and positive numbers stay positive.
         *
         * ### Warning
         * You can't just convert for example the 64-bit integer `7516192768` to a 32-bit integer, it would return `-1073741824`, so the sign-bit flips, what would be fatal. Using the above algorithm it will return `1073741824`:
         * ```javascript
         * var n = 7516192768n;
         * var low = Number(n & 0x7fffffffn) | 0;
         * var sign = Number((n >> 63n) << 31n) | 0;
         * var result = sign | low;
         * var wrong = Number(n) | 0;
         * ```
         * Or Java, for example use `jshell` _(type `/exit` to leave the shell)_:
         * ```java
         * var n = 7516192768L;
         * var low = (int)(n & 0x7fffffffL);
         * var sign = (int)((n >> 63) << 31);
         * var result = sign | low;
         * var wrong = (int) n;
         * ```
         * @since 3.0
         */
        @JvmStatic
        @JsStatic
        fun numberToInt(featureNumber: Long): Int {
            val sign = ((featureNumber shr 63) shl 31).toInt()
            val low = (featureNumber and 0x7fff_ffffL).toInt()
            return sign or low
        }

        /**
         * Ensures that the given number is positive.
         * @param number the number to verify.
         * @return the given number if being positive.
         * @throws NakshaException with error [ILLEGAL_ARGUMENT][NakshaError.ILLEGAL_ARGUMENT] if the given number is not positive.
         */
        private fun requirePositiveNumber(number: Long): Long {
            if (number >= 0L) return number
            throw illegalArg("The number is negative, this requires a text: $number")
        }

        /**
         * Tries to return the [Id] from the given value.
         *
         * @param value the value to convert into an [Id].
         * @return either the given `value` cast as [Id], or the given value converted into an [Id]; otherwise `null`, if neither is possible.
         * @since 3.0
         */
        @JsStatic
        @JvmStatic
        fun fromValue(value: Any?): Id? {
            try {
                return when (value) {
                    is Id -> value
                    is String -> Id(value)
                    is Long -> if (value >= 0) Id(value) else null
                    is Int -> if (value >= 0) Id(value.toLong()) else null
                    is Short -> if (value >= 0) Id(value.toLong()) else null
                    is Byte -> if (value >= 0) Id(value.toLong()) else null
                    is Float, is Double -> {
                        // The floating point number as double.
                        val d = value.toDouble()
                        // This fixes range issues, e.g. if the value is 9223372036854775808.0
                        // it would be converted to Long.MAX_VALUE and the next long to double
                        // would convert it again to 9223372036854775808.0, but still it is an
                        // invalid value. Directly prevent these errors here.
                        if (d < Platform.MIN_SAFE_INT || d > Platform.MAX_SAFE_INT) return null
                        // The floating point number as long.
                        val v = d.toLong()
                        // The long converted back into a floating point number and therefore,
                        // the expected value of the given floating point number.
                        // In other words: This only works, when the given value can be converted
                        // into long and back without losing information, which is only true, if
                        // double represents a 53-bit integer.
                        val e = v.toDouble()
                        if (e == d && v >= 0) Id(v) else null
                    }
                    else -> null
                }
            } catch (_: Exception) {
                return null
            }
        }

        /**
         * The identifier of the administration catalog, fixed to `naksha~admin` / `0`. It can be found in any database under Naksha control.
         *
         * **Note**: The feature of the administration catalog is an immutable feature needed to bootstrap a Naksha controlled database, therefore it is not persisted anywhere.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val ADMIN_CATALOG_ID = Id(ADMIN_CATALOG_NUMBER, ADMIN_CATALOG_TEXT)

        /**
         * The identifier of the collections-collection, the collection in which the collection-features of each catalog are persisted.
         *
         * This collection exists in every catalog under Naksha management. The identifier of the collection itself is fixed to `naksha~collections` / `0`. The feature of the collections-collection is an immutable feature. It is needed to bootstrap a new catalog.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val COLLECTIONS_COL_ID = Id(COLLECTIONS_COL_NUMBER, COLLECTIONS_COL_TEXT)

        /**
         * The identifier of the collection in which transactions are stored, located in the [admin-catalog][ADMIN_CATALOG_ID] _(`naksha~transactions`, `1`)_.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TRANSACTIONS_COL_ID = Id(TRANSACTIONS_COL_NUMBER, TRANSACTIONS_COL_TEXT)

        /**
         * The identifier of the collection in which catalogs are stored, located only within the [admin-catalog][ADMIN_CATALOG_ID] _(`naksha~catalogs` / `2`)_.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val CATALOGS_COL_ID = Id(CATALOGS_COL_NUMBER, CATALOGS_COL_TEXT)

        /**
         * The identifier of the collection in which books (global JBON2 dictionaries) are stored, located in the [admin-map][ADMIN_CATALOG_ID] _(`naksha~books` / `3`)_.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val BOOKS_COL_ID = Id(BOOKS_COL_NUMBER, BOOKS_COL_TEXT)

        /**
         * Returns the partition-number from the given feature-id.
         *
         * This is basically just an unsigned 16-bit integer, extracted from the lowest 16-bit of the feature-number. When there are less than 65536 partitions, the value must be divided by the number of real partitions, and the rest indexes the partition, for example for 4 partitions do `partitionNumber(featureNumber) % 4`, what will be a value between 0 and 3.
         * @param id the feature-id.
         * @return the partition-number.
         * @since 3.0
         * @see Id.partitionNumber
         * @see Id.partitionIndex
         */
        @JsName("featureNumberById")
        @JsStatic
        @JvmStatic
        fun partitionNumber(id: String): Int = partitionNumber(textToNumber(id))

        /**
         * Returns the partition-number from the given feature-number.
         *
         * This is basically just an unsigned 16-bit integer, extracted from the lowest 16-bit of the feature-number. When there are less than 65536 partitions, the value must be divided by the number of real partitions, and the rest indexes the partition, for example for 4 partitions do `partitionNumber(featureNumber) % 4`, what will be a value between 0 and 3.
         * @param number the feature-number.
         * @return the partition-number.
         * @since 3.0
         * @see Id.partitionNumber
         * @see Id.partitionIndex
         */
        @JsStatic
        @JvmStatic
        fun partitionNumber(number: Long): Int = number.toInt() and 0xffff
    }
}
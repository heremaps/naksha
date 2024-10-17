@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * Wrapper for a version.
 *
 * The version is a transaction-number, being a 56-bit integer, split into four parts:
 * - Year: The year in which the transactions started (e.g. 2024).
 * - Month: The month of the year in which the transaction started (1 to 12, e.g. 9 for September).
 * - Day: The day of the month in which the transaction started (1 to 31).
 * - Seq: The local sequence-number in this day.
 *
 * Every day starts with the sequence-number reset to zero. The final 64-bit value is combined as:
 * - 23-bit year, between 0 and 8388607 {shift-by 41}.
 * - 4-bit month, between 1 (January) and 12 (December) {shift-by 37}.
 * - 5-bit day, between 1 and 31 {shift-by 32}.
 * - 32-bit unsigned sequence number.
 *
 * This concept allows up to 4 billion transactions per day (between 0 and 4,294,967,295, 2^32-1). It will overflow in browsers in the year 4096, because in that year the transaction number needs 53-bit to be encoded, which is beyond the precision of a double floating point number. Should there be more than 4 billion transaction in a single day, this will overflow into the next day and potentially into an invalid day, should it happen at the last day of a given month. We ignore this situation, it seems currently impossible. Check in the browser:
 *
 * ```
 * ((4095n << 41n)+(12n << 37n)+(31n << 32n)+4294967295n) <= BigInt(Number.MAX_SAFE_INTEGER) : true
 * (4096n << 41n) <= BigInt(Number.MAX_SAFE_INTEGER): false
 * ```
 *
 * The human-readable representation as a string ([toString]) is: `{year}:{month}:{day}:{seq}`
 *
 * @property txn the transaction number, a 64-bit integer.
 */
@JsExport
open class Version(@JvmField val txn: Int64) : Comparable<Version> {

    /**
     * Convert a transaction number, given as long, into a version.
     * @param txn the transaction number.
     */
    @Suppress("NON_EXPORTABLE_TYPE")
    @JsName("fromLong")
    constructor(txn: Long) : this(Int64(txn))

    companion object VersionCompanion {
        /**
         * Create the version number from a double (in JavaScript, number).
         * @param v the version number encoded in a double.
         * @return the version wrapper.
         */
        @JsStatic
        @JvmStatic
        fun fromDouble(v: Double) : Version = Version(Int64(v))

        /**
         * Creates the version number from a string representation, being either a pure decimal number of the transaction-number (so a 64-bit unsigned integer) or the stringified human-readable variant.
         * - Throws [NakshaError.ILLEGAL_ARGUMENT], if the given string is of an invalid format.
         * @param s the string representation.
         * @return the version.
         */
        @JsStatic
        @JvmStatic
        fun fromString(s: String) : Version {
            try {
                if (s.indexOf(':') >= 0) {
                    val parts = s.split(':')
                    if (parts.size != 4) throw Exception("Too many parts")
                    return of(parts[0].toInt(), parts[1].toInt(), parts[0].toInt(), Int64(parts[0].toLong()))
                } else {
                    return Version(Int64(s.toLong()))
                }
            } catch (e: Exception) {
                throw NakshaException(NakshaError.ILLEGAL_ARGUMENT, "Invalid version string: $s")
            }
        }

        /**
         * Create a version from its parts.
         * @param year the year to encode, between 0 and 8388608 (23-bit).
         * @param month the month to encode, between 1 and 12 (4-bit).
         * @param day the day to encode, between 1 and 31 (5-bit).
         * @param seq the sequence number with in the day, between 0 and 4294967295 (32-bit).
         */
        @JvmStatic
        @JsStatic
        fun of(year: Int, month: Int, day: Int, seq: Int64): Version =
            Version((Int64(year) shl 41) or (Int64(month) shl 37) or (Int64(day) shl 32) + seq)

        /**
         * The undefined version, being used when new [Tuple]'s are created.
         */
        @JvmField
        @JsStatic
        val UNDEFINED = Version(0L)

        /**
         * The minimum value of the sequence, so just zero.
         */
        @JvmField
        @JsStatic
        val SEQ_MIN = Int64(0)

        /**
         * The maximum value for the sequence, can be used as well as bitmask.
         */
        @JvmField
        @JsStatic
        val SEQ_MAX = Int64(0x0000_0000_ffff_ffff)

        /**
         * The value to be added to calculate the end of a day.
         */
        @JvmField
        @JsStatic
        val SEQ_NEXT = Int64(0x0000_0001_0000_0000)
     }

    private var _year = -1

    /**
     * The year when the version started, a value between 0 and 8388608 (23-bit).
     */
    fun year(): Int {
        if (_year < 0) _year = (txn ushr 41).toInt()
        return _year
    }

    private var _month = -1

    /**
     * The month when the version started, a value between 1 and 12 (4-bit).
     */
    fun month(): Int {
        if (_month < 0) _month = (txn ushr 37).toInt() and 15
        return _month
    }

    private var _day = -1

    /**
     * The day when the version started, a value between 1 and 12 (4-bit).
     */
    fun day(): Int {
        if (_day < 0) _day = (txn ushr 32).toInt() and 31
        return _day
    }

    private var _seq: Int64? = null

    /**
     * The sequence number within the day, a value between 0 and 4294967295 (32-bit).
     */
    fun seq(): Int64 {
        if (_seq == null) _seq = txn and SEQ_MAX
        return _seq!!
    }

    private lateinit var _string: String

    override fun equals(other: Any?): Boolean {
        if (other is Int64) return txn eq other
        if (other is Version) return txn eq other.txn
        return false
    }

    override fun compareTo(other: Version): Int {
        val diff = txn.minus(other.txn)
        return if (diff.eq(0)) 0 else if (diff < 0) -1 else 1
    }

    override fun hashCode(): Int = txn.hashCode()

    /**
     * Returns version number as string.
     * @return `{year}:{month}:{day}:{seq}`
     */
    override fun toString(): String {
        if (!this::_string.isInitialized) _string = "${year()}:${month()}:${day()}:${seq()}"
        return _string
    }
}
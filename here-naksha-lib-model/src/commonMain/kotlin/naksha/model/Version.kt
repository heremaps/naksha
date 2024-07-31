@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * Wrapper for a version number.
 *
 * Every version persists out of _year_, _month_, and _day_ when the change started, and a unique sequence number within that day. The version number is encoded in a way, so that it can be stored either as 64-bit integer, or as double (it does only use 52 bit).
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
         * Creates the version wrapper from the feature identifier of a [naksha.model.objects.Transaction].
         * @param transactionId the [id][naksha.model.objects.Transaction.id] of the [transaction][naksha.model.objects.Transaction].
         * @return the version.
         */
        @JsStatic
        @JvmStatic
        fun fromId(transactionId: String) : Version = Version(Int64(transactionId.toLong()))

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

    /**
     * Convert the version into a transaction identifier.
     * @return the transaction identifier of this version.
     */
    fun toId(): String = txn.toString()
}
@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import com.here.naksha.lib.jbon.*
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A helper class to disassemble a Naksha Transaction.
 * @property year The year when the transaction started (UTC).
 * @property month The month when the transaction started (UTC).
 * @property day The day when the transaction started (UTC).
 * @property seq The sequence identifier inside the day.
 */
@JsExport
class NakshaTxn(val value: BigInt64) : Comparable<NakshaTxn> {
    companion object {
        /**
         * The minimum value of the sequence, so just zero.
         */
        val SEQ_MIN = BigInt64(0)

        /**
         * The maximum value for the sequence, can be used as well as bitmask.
         */
        val SEQ_MAX = BigInt64(0x0000_03ff_ffff_ffff)

        /**
         * The value to be added to calculate the end of a day.
         */
        val SEQ_NEXT = BigInt64(0x0000_0400_0000_0000)

        /**
         * Create a transaction number from its parts.
         * @param year The year to encode, between 0 and 8191.
         * @param month The month to encode, between 1 and 12.
         * @param day The day to encode, between 1 and 31.
         * @param seq The sequence in the day, between 0 and 2^42-1.
         */
        fun of(year:Int, month:Int, day:Int, seq: BigInt64) : NakshaTxn =
                NakshaTxn((BigInt64(year) shl 51) or (BigInt64(month) shl 47) or (BigInt64(day) shl 42) add seq)
    }

    val year = (value ushr 51).toInt()
    val month = (value ushr 47).toInt() and 15
    val day = (value ushr 42).toInt() and 31
    val seq = value and SEQ_MAX
    private lateinit var _tablePostfix : String
    private lateinit var _string: String
    private lateinit var _uuid: NakshaUuid

    /**
     * Translate the transaction number into a table postfix in the format _yyyy_mm_dd_.
     * @return The table postfix.
     */
    fun historyPostfix() : String {
        if (!this::_tablePostfix.isInitialized) {
            val sb = StringBuilder()
            sb.append(year)
            sb.append('_')
            if (month < 10) sb.append('0')
            sb.append(month)
            sb.append('_')
            if (day < 10) sb.append('0')
            sb.append(day)
            _tablePostfix = sb.toString()
        }
        return _tablePostfix
    }

    override fun equals(other: Any?):Boolean {
        if (other is BigInt64) return value eq other
        if (other is NakshaTxn) return value eq other.value
        return false
    }

    override fun compareTo(other: NakshaTxn): Int {
        val diff = value sub other.value
        return if (diff eqi 0) 0 else if (diff lti 0) -1 else 1
    }

    override fun hashCode() : Int {
        return value.hashCode()
    }

    override fun toString() : String {
        if (!this::_string.isInitialized) _string = "$year:$month:$day:$seq"
        return _string
    }

    fun toUuid(storageId: String) : NakshaUuid {
        if (!this::_uuid.isInitialized) _uuid = NakshaUuid(storageId, "txn", year, month, day, seq)
        return _uuid
    }
}
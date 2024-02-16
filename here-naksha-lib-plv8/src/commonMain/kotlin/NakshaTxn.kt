@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8

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
    val year = (value ushr 51).toInt()
    val month = (value ushr 47).toInt()
    val day = (value ushr 42).toInt()
    val seq = value and BigInt64(0x0000_03ff_ffff_ffff)
    private lateinit var string: String
    private lateinit var uuid: NakshaUuid

    companion object {
        /**
         * Create a transaction number from its parts.
         * @param year The year to encode, between 0 and 8191.
         * @param month The month to encode, between 1 and 12.
         * @param day The day to encode, between 1 and 31.
         * @param seq The sequence in the day, between 0 and 2^42-1.
         */
        fun of(year:Int, month:Int, day:Int, seq: BigInt64) : NakshaTxn =
                NakshaTxn((BigInt64(year) shl 51) or (BigInt64(month) shl 47) or (BigInt64(day) shl 42) or seq)
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
        if (!this::string.isInitialized) string = "$year:$month:$day:$seq"
        return string
    }

    fun toUuid(storageId: String) : NakshaUuid {
        if (!this::uuid.isInitialized) uuid = NakshaUuid(storageId, "txn", year, month, day, seq)
        return uuid
    }
}
@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

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
        fun of(year: Int, month: Int, day: Int, seq: BigInt64): NakshaTxn =
                NakshaTxn((BigInt64(year) shl 51) or (BigInt64(month) shl 47) or (BigInt64(day) shl 42) add seq)
    }

    private var _year = -1
    fun year(): Int {
        if (_year < 0) _year = (value ushr 51).toInt()
        return _year
    }

    private var _month = -1
    fun month(): Int {
        if (_month < 0) _month = (value ushr 47).toInt() and 15
        return _month
    }

    private var _day = -1
    fun day(): Int {
        if (_day < 0) _day = (value ushr 42).toInt() and 31
        return _day
    }

    private var _seq: BigInt64? = null
    fun seq(): BigInt64 {
        if (_seq == null) _seq = value and SEQ_MAX
        return _seq!!
    }

    private lateinit var _tablePostfix: String
    private lateinit var _string: String
    private lateinit var _uuid: NakshaUuid

    /**
     * Translate the transaction number into a table postfix (currently only the year as "yyyy").
     * @return The table postfix.
     */
    fun historyPostfix(): String {
        if (!this::_tablePostfix.isInitialized) _tablePostfix = "${year()}"
        return _tablePostfix
    }

    override fun equals(other: Any?): Boolean {
        if (other is BigInt64) return value eq other
        if (other is NakshaTxn) return value eq other.value
        return false
    }

    override fun compareTo(other: NakshaTxn): Int {
        val diff = value sub other.value
        return if (diff eqi 0) 0 else if (diff lti 0) -1 else 1
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    override fun toString(): String {
        if (!this::_string.isInitialized) _string = "" + year() + ":" + month() + ":" + day() + ":" + seq()
        return _string
    }

    /**
     * Convert this transaction number into a UUID representation.
     * @param storageId The storage-identifier where this transaction is stored.
     * @return The transaction number as UUID.
     */
    fun toUuid(storageId: String): NakshaUuid {
        if (!this::_uuid.isInitialized) _uuid = NakshaUuid(storageId, "txn", year(), month(), day(), seq(), 0)
        return _uuid
    }

    /**
     * Create a new UUID for a feature state that is part of this transaction.
     * @param storageId The storage-identifier where this transaction is stored.
     * @param collectionId The collection-identifier in which the feature is located.
     * @param uid The unique row identifier of the feature.
     * @return The UUID for this new feature state.
     */
    fun newFeatureUuid(storageId: String, collectionId: String, uid: Int): NakshaUuid = NakshaUuid(storageId, collectionId, year(), month(), day(), seq(), uid)
}
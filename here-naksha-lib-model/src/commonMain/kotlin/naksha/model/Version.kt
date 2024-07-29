@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.model.NakshaUtil.NakshaUtilCompanion.VERSIONS_COL_ID
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * Wrapper for a transaction number. The Naksha specification clarifies the parts of a transaction number as being the _year_, _month_, and _day_ when the transaction started, and a unique sequence number within that day.
 * @property value the raw value as 64-bit integer.
 */
@JsExport
class Version(val value: Int64) : Comparable<Version> {

    /**
     * Create the transaction number from a double (in JavaScript, number).
     * @param v the transaction number encoded in a double.
     */
    @JsName("of")
    constructor(v: Double) : this(Int64(v))

    companion object VersionCompanion {
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
     * The year when the transaction happened, a value between 0 and 8388608 (23-bit).
     */
    fun year(): Int {
        if (_year < 0) _year = (value ushr 41).toInt()
        return _year
    }

    private var _month = -1

    /**
     * The month when the transaction happened, a value between 1 and 12 (4-bit).
     */
    fun month(): Int {
        if (_month < 0) _month = (value ushr 37).toInt() and 15
        return _month
    }

    private var _day = -1

    /**
     * The day when the transaction happened, a value between 1 and 12 (4-bit).
     */
    fun day(): Int {
        if (_day < 0) _day = (value ushr 32).toInt() and 31
        return _day
    }

    private var _seq: Int64? = null

    /**
     * The sequence number within the day, a value between 0 and 4294967295 (32-bit).
     */
    fun seq(): Int64 {
        if (_seq == null) _seq = value and SEQ_MAX
        return _seq!!
    }

    private lateinit var _tablePostfix: String
    private lateinit var _string: String
    private lateinit var _guid: Guid

    override fun equals(other: Any?): Boolean {
        if (other is Int64) return value eq other
        if (other is Version) return value eq other.value
        return false
    }

    override fun compareTo(other: Version): Int {
        val diff = value.minus(other.value)
        return if (diff.eq(0)) 0 else if (diff < 0) -1 else 1
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    /**
     * Returns transaction number as string.
     * @return `{year}:{month}:{day}:{seq}`
     */
    override fun toString(): String {
        if (!this::_string.isInitialized) _string = "${year()}:${month()}:${day()}:${seq()}"
        return _string
    }

    /**
     * Create a [Guid] for the transaction itself.
     * @param storageId the storage-identifier of the storage in which this version is stored.
     * @param map the map in which this version is stored.
     * @return the [Guid] of that feature.
     */
    fun toGuid(storageId: String, map: String): Guid {
        if (!this::_guid.isInitialized) _guid = Guid(storageId, map, VERSIONS_COL_ID, toString(), Luid(this, 0))
        return _guid
    }

    /**
     * Create a [Guid] for a specific feature state, being part of this version.
     * @param storageId the storage-identifier of the storage in which this version is stored.
     * @param map the map in which this version is stored.
     * @param collectionId the collection-identifier in which the feature is stored.
     * @param featureId the feature-identifier.
     * @param uid the unique state identifier within the version.
     * @return the [Guid] that describes this state world-wide uniquely.
     */
    fun newFeatureGuid(storageId: String, map: String, collectionId: String, featureId: String, uid: Int): Guid =
        Guid(storageId, map, collectionId, featureId, Luid(this, uid))
}
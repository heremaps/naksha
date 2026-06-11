@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.base.Timestamp
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Wrapper for a version (transaction number), encoded as an unsigned 56-bit integer (the upper 8 bits are always zero).
 *
 * There are two kinds of versions:
 *
 * ### Dated version (`isDated() == true`, year ≥ 16)
 *
 * Bits are laid out as follows (MSB → LSB):
 * ```
 * | 63–56        | 55–41       | 40–37       | 36–32     | 31–2          | 1–0          |
 * | 8-bit (zero) | 15-bit year | 4-bit month | 5-bit day | 30-bit seq    | 2-bit action |
 * ```
 * - **year** (`txn ushr 41`): calendar year, must be ≥ 16 and ≤ 32767. JavaScript-safe up to year 4095
 *   (53-bit precision limit: `(4095 shl 41) + ...` still fits in a JS double).
 * - **month** (`(txn ushr 37) and 0xF`): 1–12.
 * - **day** (`(txn ushr 32) and 0x1F`): 1–31.
 * - **seq** (`(txn ushr 2) and 0x3FFF_FFFF`): 30-bit sequence number within the day, 0–1073741823.
 * - **action** (`txn and 3`): lower 2 bits, see [Action].
 *
 * Use [auto] to construct a dated version.
 *
 * ### Manual version (`isManualVersion() == true`, year < 16)
 *
 * The upper 21 bits (63–43) are always zero. The lower 43 bits hold an arbitrary value, with bits 1–0
 * still encoding the [Action]. Manual versions are hand-assigned and not timestamp-derived.
 *
 * Use [manual] to construct a manual version.
 *
 * ### String representation
 *
 * [toString] returns the raw [txn] value as a plain decimal number, regardless of whether the
 * version is dated or manual. [fromString] accepts both the decimal form and the legacy
 * `{year}:{month}:{day}:{seqWithAction}` form for backward-compatibility.
 *
 * @property txn the raw 64-bit transaction number (upper 8 bits always zero).
 * @since 3.0
 */
@JsExport
open class Version(@JvmField val txn: Int64) : Comparable<Version> {

    /**
     * Convert a transaction number given as [Long] into a version.
     * @param txn the transaction number.
     * @since 3.0
     */
    @Suppress("NON_EXPORTABLE_TYPE")
    @JsName("fromLong")
    constructor(txn: Long) : this(Int64(txn))

    companion object VersionCompanion {

        /** Maximum year value (15-bit, JS-safe upper bound). */
        private const val YEAR_MAX = 32767
        /** Minimum year for a dated version. */
        private const val YEAR_MIN = 16

        /** Mask for the 30-bit sequence field. */
        private val SEQ_30_MASK = Int64(0x3FFF_FFFF)

        /** Mask for the 41-bit manual-version seq field (upper 21 bits of the 64-bit value must be 0). */
        private val MANUAL_SEQ_MASK = Int64(0x1FF_FFFF_FFFF) // 41 bits

        /**
         * Create a version from a double (JavaScript number).
         * @param v the version number encoded as a double.
         * @since 3.0
         */
        @JsStatic
        @JvmStatic
        fun fromDouble(v: Double): Version = Version(Int64(v))

        /**
         * Creates a version from its string representation.
         *
         * Accepts either:
         * - A pure decimal encoding of the 64-bit [txn] value.
         * - The human-readable form `{year}:{month}:{day}:{seq}` (seq is the 30-bit sequence, no action bits).
         *
         * Throws [NakshaError.ILLEGAL_ARGUMENT] if the string is invalid.
         * @param s the string representation.
         * @since 3.0
         */
        @JsStatic
        @JvmStatic
        fun fromString(s: String): Version {
            try {
                if (s.indexOf(':') >= 0) {
                    val parts = s.split(':')
                    if (parts.size != 4) throw Exception("Expected 4 colon-separated parts")
                    // The 4th field carries the raw lower 32 bits of the txn (including action bits in 1-0).
                    val year  = parts[0].toInt()
                    val month = parts[1].toInt()
                    val day   = parts[2].toInt()
                    val seqRaw = Int64(parts[3].toLong()) // raw lower 32 bits, includes action in bits 1-0
                    val txn = (Int64(year) shl 41) or (Int64(month) shl 37) or (Int64(day) shl 32) or seqRaw
                    return Version(txn)
                } else {
                    return Version(Int64(s.toLong()))
                }
            } catch (_: Exception) {
                throw NakshaException(NakshaError.ILLEGAL_ARGUMENT, "Invalid version string: $s")
            }
        }

        /**
         * Constructs a **dated** version from its components.
         *
         * Validates all arguments against their allowed ranges and throws [IllegalArgumentException] if any
         * value is out of range.
         *
         * @param year  calendar year; must be in 16..32767.
         * @param month month of the year; must be in 1..12.
         * @param day   day of the month; must be in 1..31.
         * @param seq   30-bit sequence number within the day; must be in 0..1073741823 (0x3FFF_FFFF).
         * @param action the [Action] to encode in the lower 2 bits; defaults to [Action.CREATED].
         * @since 3.0
         */
        @JvmStatic
        @JsStatic
        @JvmOverloads
        fun auto(year: Int, month: Int, day: Int, seq: Int64, action: Action = Action.CREATED): Version {
            require(year in YEAR_MIN..YEAR_MAX) {
                "year must be in $YEAR_MIN..$YEAR_MAX, got $year"
            }
            require(month in 1..12) { "month must be in 1..12, got $month" }
            require(day in 1..31)   { "day must be in 1..31, got $day" }
            require(seq >= Int64(0) && seq <= SEQ_30_MASK) {
                "seq must be in 0..${SEQ_30_MASK.toLong()} (30-bit), got $seq"
            }
            val txn = (Int64(year) shl 41) or
                      (Int64(month) shl 37) or
                      (Int64(day) shl 32) or
                      (seq shl 2) or
                      Int64(action.intValue)
            return Version(txn)
        }

        /**
         * Constructs a **manual** version.
         *
         * The resulting [txn] must have its upper 21 bits (63–43) all zero, which means the effective
         * value fits in 43 bits. The [seq] therefore must be in 0..0x1FF_FFFF_FFFF (41 bits), since
         * the lower 2 bits are reserved for [action].
         *
         * Throws [IllegalArgumentException] if [seq] is out of range.
         *
         * @param seq    41-bit sequence value; must be in 0..0x1FF_FFFF_FFFF.
         * @param action the [Action] to encode in the lower 2 bits; defaults to [Action.CREATED].
         * @since 3.0
         */
        @JvmStatic
        @JsStatic
        @JvmOverloads
        fun manual(seq: Int64, action: Action = Action.CREATED): Version {
            require(seq >= Int64(0) && seq <= MANUAL_SEQ_MASK) {
                "seq for a manual version must be in 0..${MANUAL_SEQ_MASK.toLong()} (41-bit), got $seq"
            }
            return Version((seq shl 2) or Int64(action.intValue))
        }

        /**
         * Creates a dated version for the current wall-clock time.
         *
         * @param seq    30-bit sequence number within the current day; must be in 0..1073741823.
         * @param action the [Action] to encode; defaults to [Action.CREATED].
         * @since 3.0
         */
        @JvmStatic
        @JsStatic
        @JvmOverloads
        fun now(seq: Int64, action: Action = Action.CREATED): Version {
            val now = Timestamp.now()
            return auto(now.year, now.month, now.day, seq, action)
        }

        /**
         * The _HEAD_ sentinel version _(9_007_199_254_740_991L aka `2^53-1`)_.
         *
         * When a [Tuple] is the current HEAD state its `nextVersion` is synthesised as this value
         * (the column is not physically stored in HEAD tables).
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val HEAD = Version(9_007_199_254_740_991L)
        // = 2^53-1, aka Number.MAX_SAFE_INTEGER
        // 3n + (1073741823n << 2n) + (31n << 32n) + (15n << (32n+5n)) + (4095n << (32n+5n+4n)) = 9007199254740991n
        // bitwise: 0x001f_ffff_ffff_ffff

        /**
         * The minimum valid dated version (year=16, month=1, day=1, seq=0, action=CREATED).
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val MIN = auto(16, 1, 1, Int64(0), Action.CREATED)
        // 0n + (0n << 2n) + (1n << 32n) + (1n << (32n+5n)) + (16n << (32n+5n+4n)) = 35326106009600n
        // bitwise: 0x0000_2021_0000_0000

        /**
         * The maximum valid dated version (year=4095, month=12, day=31, seq=1,073,741,823, action=VERSION).
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val MAX = auto(4095, 12, 31, Int64(1_073_741_823), Action.VERSION)
        // 3n + (1073741823n << 2n) + (31n << 32n) + (12n << (32n+5n)) + (4095n << (32n+5n+4n)) = 9006786937880575n
        // bitwise: 0x001f_ff9f_ffff_ffff

        /**
         * The minimum value of the 30-bit sequence field (zero).
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val SEQ_MIN: Int64 = Int64(0)

        /**
         * The maximum value of the 30-bit sequence field (`0x3FFF_FFFF` = 1073741823).
         * Also usable as a bitmask to extract the sequence from a shifted value.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val SEQ_MAX: Int64 = SEQ_30_MASK

        /**
         * The raw increment to add to [txn] to advance the sequence counter by one while keeping the
         * action bits unchanged. Equal to `1 shl 2` = `4`.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val SEQ_INC: Int64 = Int64(1) shl 2
    }

    private var _year = -1

    /**
     * The year component of a dated version (`txn ushr 41`).
     * For manual versions (year < 16) this value has no calendar meaning.
     * @since 3.0
     */
    val year: Int
        get() {
            if (_year < 0) _year = (txn ushr 41).toInt()
            return _year
        }

    private var _month = -1

    /**
     * The month component of a dated version (`(txn ushr 37) and 0xF`), 1–12.
     * @since 3.0
     */
    val month: Int
        get() {
            if (_month < 0) _month = (txn ushr 37).toInt() and 0xF
            return _month
        }

    private var _day = -1

    /**
     * The day component of a dated version (`(txn ushr 32) and 0x1F`), 1–31.
     * @since 3.0
     */
    val day: Int
        get() {
            if (_day < 0) _day = (txn ushr 32).toInt() and 0x1F
            return _day
        }

    private var _seq: Int64? = null

    /**
     * The 30-bit sequence number (`(txn ushr 2) and 0x3FFF_FFFF`).
     *
     * For dated versions this is the sequence within the day (0–1073741823).
     * For manual versions this is the upper 30 bits of the 41-bit seq value passed to [manual].
     * @since 3.0
     */
    val seq: Int64
        get() {
            var s = _seq
            if (s == null) {
                s = (txn ushr 2) and SEQ_MAX
                _seq = s
            }
            return s
        }

    /**
     * Returns `true` if this is a **dated** version, i.e. the year field (`txn ushr 41`) is ≥ 16.
     * @since 3.0
     */
    fun isDated(): Boolean = (txn ushr 41).toInt() >= 16

    /**
     * Returns `true` if this is a **manual** version, i.e. the year field is < 16 and the upper 21 bits are zero.
     * This is the logical inverse of [isDated].
     * @since 3.0
     */
    fun isManualVersion(): Boolean = !isDated()

    /**
     * Returns the [Action] encoded in the lower 2 bits of [txn].
     * @since 3.0
     */
    fun action(): Action = Action.fromValue(txn.toInt() and 3)

    private var _string: String? = null

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
     * Returns the version as a plain decimal string of the raw [txn] value.
     *
     * This representation is lossless for all version types (dated and manual) and survives
     * a round-trip through [fromString].  The legacy `{year}:{month}:{day}:{seqWithAction}`
     * format is no longer emitted; [fromString] still accepts it for backward-compatibility.
     * @since 3.0
     */
    override fun toString(): String {
        var s = _string
        if (s == null) {
            s = txn.toLong().toString()
            _string = s
        }
        return s
    }
}

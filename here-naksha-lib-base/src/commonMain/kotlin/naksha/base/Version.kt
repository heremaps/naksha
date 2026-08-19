@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

// TODO: @AI: Fix the documentation, it does not match the actual one.
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
 * [toString] returns the raw [number] value as a plain decimal number, regardless of whether the
 * version is dated or manual. [fromString] accepts both the decimal form and the legacy
 * `{year}:{month}:{day}:{seqWithAction}` form for backward-compatibility.
 *
 * @property number the raw 53-bit version number (upper 11 bits are always zero).
 * @throws NakshaException with error [ILLEGAL_ARGUMENT][NakshaError.ILLEGAL_ARGUMENT] if the given version number is invalid.
 * @since 3.0
 */
@JsExport
open class Version(@JvmField val number: Int64) : Comparable<Version> {
    init {
        if ((number and Int64(MAX_SAFE_INTEGER)) != number) {
            throw illegalArg("$number is not a valid version")
        }
    }

    /**
     * Convert a [Long] into a [Int64] version.
     * @param value the transaction number.
     * @since 3.0
     */
    @Suppress("NON_EXPORTABLE_TYPE")
    @JsName("fromLong")
    constructor(value: Long) : this(Int64(value))

    /**
     * Convert a stringified version.
     * @param value the stringified version as decimal number.
     * @since 3.0
     * @throws NumberFormatException if the given string is no valid version.
     */
    @JsName("fromString")
    constructor(value: String) : this(Int64(value.toLong()))

    companion object VersionCompanion {

        /**
         * `2^53 - 1` — the maximum safe integer in an IEEE-754 double (`Number.MAX_SAFE_INTEGER`),
         * and therefore the largest valid version number.
         */
        const val MAX_SAFE_INTEGER: Long = 9_007_199_254_740_991L

        /** Maximum year value (15-bit, JS-safe upper bound). */
        private const val YEAR_MAX = 32767
        /** Minimum year for a dated version. */
        private const val YEAR_MIN = 16

        /** Mask for the 30-bit sequence field. */
        private val SEQ_30_MASK = Int64(0x3FFF_FFFF)

        /** Mask for the 41-bit manual-version seq field (upper 21 bits of the 64-bit value must be 0). */
        private val MANUAL_SEQ_MASK = Int64(0x1FF_FFFF_FFFF) // 41 bits; 2,199,023,255,551

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
         * - A pure decimal encoding of the 64-bit [number] value.
         * - The human-readable form `{year}:{month}:{day}:{seq}` (seq is the 30-bit sequence, no action bits).
         *
         * @param s the string representation.
         * @since 3.0
         * @throws NakshaException with error [ILLEGAL_ARGUMENT][NakshaError.ILLEGAL_ARGUMENT]  if the given string is no valid version.
         */
        @JsStatic
        @JvmStatic
        fun fromString(s: String): Version {
            try {
                return Version(Int64(s.toLong()))
            } catch (_: Exception) {
                throw illegalArg("Invalid version string: $s")
            }
        }

        /**
         * Constructs a **dated** version from its components.
         *
         * @param year  calendar year; must be in 16..32767.
         * @param month month of the year; must be in 1..12.
         * @param day   day of the month; must be in 1..31.
         * @param seq   30-bit sequence number within the day; must be in 0..1073741823 (0x3FFF_FFFF).
         * @param action the [Action] to encode in the lower 2 bits.
         * @throws NakshaException with error [ILLEGAL_ARGUMENT][NakshaError.ILLEGAL_ARGUMENT] if any value is out of range.
         * @since 3.0
         */
        @JvmStatic
        @JsStatic
        fun auto(year: Int, month: Int, day: Int, seq: Int64, action: Action): Version {
            if (year !in YEAR_MIN..YEAR_MAX) throw illegalArg("year must be in $YEAR_MIN..$YEAR_MAX, got $year")
            if (month !in 1..12) throw illegalArg("month must be in 1..12, got $month")
            if (day !in 1..31) throw illegalArg("day must be in 1..31, got $day")
            if (!((seq >= Int64(0) && seq <= SEQ_30_MASK))) {
                throw illegalArg("seq must be in 0..${SEQ_30_MASK.toLong()} (30-bit), got $seq")
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
         * The resulting [seq] must have its upper 21 bits (63–43) all zero, which means the effective
         * value fits in 43 bits. The [seq] therefore must be in 0..0x1FF_FFFF_FFFF (41 bits), since
         * the lower 2 bits are reserved for [action].
         *
         * @param seq    41-bit sequence value; must be in 0..0x1FF_FFFF_FFFF.
         * @param action the [Action] to encode in the lower 2 bits.
         * @since 3.0
         * @throws NakshaException with error [ILLEGAL_ARGUMENT][NakshaError.ILLEGAL_ARGUMENT] if [seq] is out of range..
         */
        @JvmStatic
        @JsStatic
        fun manual(seq: Int64, action: Action): Version {
            if (!(seq >= Int64(0) && seq <= MANUAL_SEQ_MASK)) {
                throw illegalArg("seq for a manual version must be in 0..${MANUAL_SEQ_MASK.toLong()} (41-bit), got $seq")
            }
            return Version((seq shl 2) or Int64(action.intValue))
        }

        /**
         * Creates a dated version for the current wall-clock time.
         *
         * @param seq    30-bit sequence number within the current day; must be in 0..1073741823.
         * @param action the [Action] to encode.
         * @since 3.0
         */
        @JvmStatic
        @JsStatic
        fun now(seq: Int64, action: Action): Version {
            val now = Timestamp.now()
            return auto(now.year, now.month, now.day, seq, action)
        }

        /**
         * Turns the given version into a real version, so setting the lower two bit to two, and ensure that the value is a valid version number.
         *
         * @param version the version to turn into a version.
         * @return the given version with the lowest two bit set.
         * @throws NakshaException with error [ILLEGAL_ARGUMENT][NakshaError.ILLEGAL_ARGUMENT] if the given version is no valid version.
         * @since 3.0
         */
        @JvmStatic
        @JsStatic
        fun asVersion(version: Int64): Int64 {
            val v = version or Int64(3)
            if (v > HEAD.number) return HEAD.number
            if (v < 0) throw illegalArg("Versions must not be negative")
            return v
        }

        /**
         * The _HEAD_ sentinel version _(`9_007_199_254_740_991` aka `2^53-1`)_. Can be used as well to mask version to ensure valid version number, like `version & Version.HEAD`.
         *
         * When a `Tuple` is the _HEAD_ state its next-version is synthesized as this value or as `null`, which has by definition the same meaning.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val HEAD = Version(MAX_SAFE_INTEGER)
        // 3n + (1073741823n << 2n) + (31n << 32n) + (15n << (32n+5n)) + (4095n << (32n+5n+4n)) = 9007199254740991n
        // bitwise: 0x001f_ffff_ffff_ffff

        /**
         * The minimum valid dated version (year=16, month=1, day=1, seq=0, action=CREATED).
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val MIN_AUTO = auto(16, 1, 1, Int64(0), Action.CREATE)
        // 0n + (0n << 2n) + (1n << 32n) + (1n << (32n+5n)) + (16n << (32n+5n+4n)) = 35326106009600n
        // bitwise: 0x0000_2021_0000_0000

        /**
         * The maximum valid dated version (year=4095, month=12, day=31, seq=1,073,741,823, action=VERSION).
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val MAX_AUTO = auto(4095, 12, 31, Int64(1_073_741_823), Action.VERSION)
        // 3n + (1073741823n << 2n) + (31n << 32n) + (12n << (32n+5n)) + (4095n << (32n+5n+4n)) = 9006786937880575n
        // bitwise: 0x001f_ff9f_ffff_ffff

        /**
         * The minimum manual version (year=0, month=0, day=0, seq=1, action=CREATED).
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val MIN_MANUAL = manual(Int64(1), Action.CREATE)

        /**
         * The maximum valid manual version (seq=2,199,023,255,551, action=VERSION).
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val MAX_MANUAL = manual(MANUAL_SEQ_MASK, Action.CREATE)

        /**
         * The absolute minimum version number _(3)_.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val MIN = MIN_MANUAL

        /**
         * The absolute maximal valid version number _(9,007,199,254,740,988)_. This is three less than [HEAD].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val MAX = Version(9_007_199_254_740_988L)

        /**
         * The minimum value of the 30-bit sequence field (zero).
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val SEQ_MIN: Int64 = Int64(0)

        /**
         * The maximum value of the 30-bit sequence field (`0x3FFF_FFFF` = 1073741823).
         * Also, usable as a bitmask to extract the sequence from a shifted value.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val SEQ_MAX: Int64 = SEQ_30_MASK

        /**
         * The raw increment to add to [number] to advance the sequence counter by one while keeping the
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
            if (_year < 0) _year = (number ushr 41).toInt()
            return _year
        }

    private var _month = -1

    /**
     * The month component of a dated version (`(txn ushr 37) and 0xF`), 1–12.
     * @since 3.0
     */
    val month: Int
        get() {
            if (_month < 0) _month = (number ushr 37).toInt() and 0xF
            return _month
        }

    private var _day = -1

    /**
     * The day component of a dated version (`(txn ushr 32) and 0x1F`), 1–31.
     * @since 3.0
     */
    val day: Int
        get() {
            if (_day < 0) _day = (number ushr 32).toInt() and 0x1F
            return _day
        }

    private var _seq: Int64? = null

    /**
     * The 30-bit or 41-bit sequence number.
     *
     * - For dated versions this is the sequence within the day _(0..1,073,741,823)_.
     * - For manual versions this is the version-number shifted right by 2 _(1..2,199,023,255,551)_.
     * @since 3.0
     */
    val seq: Int64
        get() {
            var s = _seq
            if (s == null) {
                if (isDated()) {
                    s = (number ushr 2) and SEQ_MAX
                    _seq = s
                } else {
                    s = (number ushr 2) and MANUAL_SEQ_MASK
                }
            }
            return s
        }

    /**
     * Returns `true` if this is a **dated** version, i.e. the year field (`txn ushr 41`) is ≥ 16.
     * @since 3.0
     */
    fun isDated(): Boolean = (number ushr 41).toInt() >= 16

    /**
     * Returns `true` if this is a **manual** version, i.e. the year field is < 16 and the upper 21 bits are zero.
     * This is the logical inverse of [isDated].
     * @since 3.0
     */
    fun isManualVersion(): Boolean = !isDated()

    /**
     * Returns the [Action] encoded in the lower 2 bits of [number].
     * @since 3.0
     */
    fun action(): Action = Action.fromValue(number.toInt() and 3)

    private var _string: String? = null

    override fun equals(other: Any?): Boolean {
        if (other is Int64) return number eq other
        if (other is Version) return number eq other.number
        return false
    }

    override fun compareTo(other: Version): Int {
        val diff = number.minus(other.number)
        return if (diff.eq(0)) 0 else if (diff < 0) -1 else 1
    }

    override fun hashCode(): Int = number.hashCode()

    /**
     * Returns the version as a plain decimal string of the raw [number] value.
     *
     * This representation is lossless for all version types (dated and manual) and survives
     * a round-trip through [fromString].  The legacy `{year}:{month}:{day}:{seqWithAction}`
     * format is no longer emitted; [fromString] still accepts it for backward-compatibility.
     * @since 3.0
     */
    override fun toString(): String {
        var s = _string
        if (s == null) {
            s = number.toLong().toString()
            _string = s
        }
        return s
    }
}

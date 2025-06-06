@file:OptIn(ExperimentalJsExport::class)
@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlinx.datetime.*
import naksha.base.Platform.PlatformCompanion.forKClass
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * A timestamp split into the values.
 * @property ts The UTC epoch-timestamp in milliseconds.
 * @property year The year, for example 2024.
 * @property month The month of the year, between 1 (January) and 12 (December)
 * @property day The day of the month, between 1 and 31.
 * @property hour The hour of the day, between 0 and 23.
 * @property minute The minute of the hour, between 0 and 59.
 * @property second The second of the minute, between 0 and 60.
 * @property millis The milliseconds of the second, between 0 and 999.
 * @property micros The microseconds of the second, between 0 and 999,999.
 * @property nanos The nanoseconds of the second, between 0 and 999,999,999.
 */
@JsExport
class Timestamp(
    val ts: Int64,
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val second: Int,
    val millis: Int,
    val micros: Int,
    val nanos: Int
) {
    companion object TimestampCompanion {
        /**
         * The [PlatformType] of [Timestamp].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<Timestamp> = forKClass(Timestamp::class).withPackageName(PACKAGE_NAME)

        /**
         * Returns the current timestamp.
         * @return The current timestamp.
         */
        @JvmStatic
        @JsStatic
        fun now() : Timestamp {
            val instant = Clock.System.now()
            val ldt = instant.toLocalDateTime(TimeZone.UTC)
            return Timestamp(
                Int64(instant.toEpochMilliseconds()),
                ldt.year,
                ldt.monthNumber,
                ldt.dayOfMonth,
                ldt.hour,
                ldt.minute,
                ldt.second,
                ldt.nanosecond / 1_000_0000,
                ldt.nanosecond / 1_000,
                ldt.nanosecond
            )
        }

        /**
         * Return the timestamp for the given epoch-millis.
         * @param ts The epoch-millis.
         * @return The timestamp.
         */
        @JvmStatic
        @JsStatic
        fun fromMillis(ts: Int64) : Timestamp {
            val instant = Instant.fromEpochMilliseconds(ts.toLong())
            val ldt = instant.toLocalDateTime(TimeZone.UTC)
            return Timestamp(
                ts,
                ldt.year,
                ldt.monthNumber,
                ldt.dayOfMonth,
                ldt.hour,
                ldt.minute,
                ldt.second,
                ldt.nanosecond / 1_000_000,
                ldt.nanosecond / 1_000,
                ldt.nanosecond
            )
        }

        /**
         * Create a timestamp from a date and time given in UTC.
         * @param year The full year, e.g. 2024.
         * @param month The month of the year, between 0 (January) and 11 (December).
         * @param day The day of the month, between 1 and 31.
         * @param hour The hour of the day, between 0 and 23.
         * @param minute The minute of the hour, between 0 and 59.
         * @param second The second of the minute, between 0 and 60.
         * @param nanos The nanoseconds of the second, between 0 and 999,999,999.
         */
        @JvmStatic
        @JsStatic
        fun fromDate(year:Int, month:Int, day:Int, hour:Int, minute:Int, second:Int, nanos:Int) : Timestamp {
            val ldt = LocalDateTime(year, month, day, hour, minute, second, nanos)
            val instant = ldt.toInstant(TimeZone.UTC)
            return Timestamp(
                Int64(instant.toEpochMilliseconds()),
                year,
                month,
                day,
                hour,
                minute,
                second,
                nanos / 1_000_000,
                nanos / 1_000,
                nanos
            )
        }
    }
}
package naksha.base

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.js.JsExport

/**
 * A helper to split epoch millis into its parts.
 * @property millis the milliseconds as epoch time.
 */
@Suppress("OPT_IN_USAGE", "unused")
@JsExport
open class Epoch(val millis: Int64 = Platform.currentMillis()) {
    private val dateTime = Instant.fromEpochMilliseconds(millis.toLong()).toLocalDateTime(TimeZone.UTC)

    /**
     * The year.
     */
    val year: Int
        get() = dateTime.year

    /**
     * The month (1 to 12).
     */
    val month: Int
        get() = dateTime.monthNumber

    /**
     * The day of the month (1 to 31).
     */
    val day: Int
        get() = dateTime.dayOfMonth

    /**
     * The hour (0 to 23) of the day.
     */
    val hour: Int
        get() = dateTime.hour

    /**
     * The minute (0 to 59) of the day.
     */
    val minute: Int
        get() = dateTime.minute

    /**
     * The second (0 to 60) of the day.
     */
    val second: Int
        get() = dateTime.second

    /**
     * The milliseconds of the second.
     */
    val millisOfSecond: Int
        get() = dateTime.nanosecond / 1000
}
package naksha.base

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import naksha.base.Platform.Platform_C.forKClass
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A helper to split epoch millis into its parts.
 * @property millis the milliseconds as epoch time.
 */
@Suppress("OPT_IN_USAGE", "unused")
@JsExport
open class Epoch(val millis: Int64 = Platform.currentMillis()) {
    companion object Epoch_C {
        /**
         * The [PlatformType] of [Epoch].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(Epoch::class).withPackageName(PACKAGE_NAME)

        init { initialize() }
    }

    private val dateTime = Instant.fromEpochMilliseconds(millis.toLong()).toLocalDateTime(TimeZone.UTC)

    /**
     * The year.
     */
    @JvmField
    val year: Int = dateTime.year

    /**
     * The month (1 to 12).
     */
    @JvmField
    val month: Int = dateTime.monthNumber

    /**
     * The day of the month (1 to 31).
     */
    @JvmField
    val day: Int = dateTime.dayOfMonth

    /**
     * The hour (0 to 23) of the day.
     */
    @JvmField
    val hour: Int = dateTime.hour

    /**
     * The minute (0 to 59) of the day.
     */
    @JvmField
    val minute: Int = dateTime.minute

    /**
     * The second (0 to 60) of the day.
     */
    @JvmField
    val second: Int = dateTime.second

    /**
     * The milliseconds of the second.
     */
    @JvmField
    val millisOfSecond: Int = dateTime.nanosecond / 1000
}
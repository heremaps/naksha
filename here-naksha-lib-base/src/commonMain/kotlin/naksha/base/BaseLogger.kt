package naksha.base

import kotlin.js.JsExport

/**
 * A simple API to access the native logging.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
interface BaseLogger {
    /**
     * Writes an debug log.
     * @param msg The message.
     * @param msgFn A function that is invoked if info-logging is enabled, and that should return either the
     * final message to log or _null_ to suppress logging for some other reason.
     */
    fun debug(msg: String? = null, msgFn: ((msg: String?) -> String?)? = null)

    /**
     * Writes an info.
     * @param msg The message.
     * @param msgFn A function that is invoked if info-logging is enabled, and that should return either the
     * final message to log or _null_ to suppress logging for some other reason.
     */
    fun info(msg: String? = null, msgFn: ((msg: String?) -> String?)? = null)

    /**
     * Writes a warning.
     * @param msg The message.
     * @param msgFn A function that is invoked if info-logging is enabled, and that should return either the
     * final message to log or _null_ to suppress logging for some other reason.
     */
    fun warn(msg: String? = null, msgFn: ((msg: String?) -> String?)? = null)

    /**
     * Writes an error.
     * @param msg The message.
     * @param msgFn A function that is invoked if info-logging is enabled, and that should return either the
     * final message to log or _null_ to suppress logging for some other reason.
     */
    fun error(msg: String? = null, msgFn: ((msg: String?) -> String?)? = null)
}
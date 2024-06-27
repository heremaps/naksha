package naksha.base

import kotlin.js.JsExport

/**
 * A simple API to access the native logging.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
interface PlatformLogger {
    /**
     * Writes an debug log.
     * @param msg The message with optional placeholders `{}` for the varargs.
     * @param args Arguments to be injected into the message.
     */
    fun debug(msg: String, vararg args: Any?)

    /**
     * Writes an debug log.
     * @param msgFn A function that is invoked if logging is enabled, and that should return either the
     * final message to log or _null_ to suppress logging for some other reason.
     */
    fun atDebug(msgFn: () -> String?)

    /**
     * Writes an info log.
     * @param msg The message with optional placeholders `{}` for the varargs.
     * @param args Arguments to be injected into the message.
     */
    fun info(msg: String, vararg args: Any?)

    /**
     * Writes an info log.
     * @param msgFn A function that is invoked if logging is enabled, and that should return either the
     * final message to log or _null_ to suppress logging for some other reason.
     */
    fun atInfo(msgFn: () -> String?)

    /**
     * Writes a warn log.
     * @param msg The message with optional placeholders `{}` for the varargs.
     * @param args Arguments to be injected into the message.
     */
    fun warn(msg: String, vararg args: Any?)

    /**
     * Writes a warn log.
     * @param msgFn A function that is invoked if logging is enabled, and that should return either the
     * final message to log or _null_ to suppress logging for some other reason.
     */
    fun atWarn(msgFn: () -> String?)

    /**
     * Writes an error log.
     * @param msg The message with optional placeholders `{}` for the varargs.
     * @param args Arguments to be injected into the message.
     */
    fun error(msg: String, vararg args: Any?)

    /**
     * Writes an error log.
     * @param msgFn A function that is invoked if logging is enabled, and that should return either the
     * final message to log or _null_ to suppress logging for some other reason.
     */
    fun atError(msgFn: () -> String?)
}
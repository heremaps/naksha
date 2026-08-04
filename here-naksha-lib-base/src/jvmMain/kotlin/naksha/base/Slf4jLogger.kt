package naksha.base

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * The standard logger for JVM, writes logs using SLF4J.
 * @since 3.0
 */
open class Slf4jLogger: IBaseLogger {
    /**
     * The logger being used for all logs, requested from SLJF4J.
     * @since 3.0
     */
    @JvmField
    var logger: Logger = LoggerFactory.getLogger("naksha.base")

    /**
     * Tests if this code is running with a debugging agent attached.
     *
     * When a debugging agent is attached, this implementation will automatically write debug logs to info-logs in the logger factory.
     * @return _true_ if a debugging agent is attached; _false_ otherwise.
     */
    fun runningWithDebugger(): Boolean {
        val runtimeMxBean = java.lang.management.ManagementFactory.getRuntimeMXBean()
        return runtimeMxBean.inputArguments.any { it.contains("-agentlib:jdwp") || it.contains("-Xdebug") }
    }

    /**
     * Will be _true_ when either running with debugging agent attached or when the environment variable `NAKSHA_DEBUG` is set to the string `true`.
     * @since 3.0
     */
    @JvmField
    var logDebugToInfo: Boolean = System.getenv("NAKSHA_DEBUG") == "true" || runningWithDebugger()

    override fun debug(msg: String, vararg args: Any?) {
        if (logDebugToInfo) logger.info(msg, *args)
        else if (BaseUtil.ENABLE_DEBUG && logger.isDebugEnabled) logger.debug(msg, *args)
    }

    override fun atDebug(msgFn: () -> String?) {
        if (logDebugToInfo) {
            val msg = msgFn.invoke()
            if (msg != null) logger.info(msg)
        } else if (BaseUtil.ENABLE_DEBUG && logger.isDebugEnabled) {
            val msg = msgFn.invoke()
            if (msg != null) logger.debug(msg)
        }
    }

    override fun info(msg: String, vararg args: Any?) {
        if (BaseUtil.ENABLE_INFO && logger.isInfoEnabled) logger.info(msg, *args)
    }

    override fun atInfo(msgFn: () -> String?) {
        if (BaseUtil.ENABLE_INFO && logger.isInfoEnabled) {
            val msg = msgFn.invoke()
            if (msg != null) logger.info(msg)
        }
    }

    override fun warn(msg: String, vararg args: Any?) {
        if (BaseUtil.ENABLE_WARN && logger.isWarnEnabled) logger.warn(msg, *args)
    }

    override fun atWarn(msgFn: () -> String?) {
        if (BaseUtil.ENABLE_WARN && logger.isWarnEnabled) {
            val msg = msgFn.invoke()
            if (msg != null) logger.warn(msg)
        }
    }

    override fun error(msg: String, vararg args: Any?) {
        if (BaseUtil.ENABLE_ERROR && logger.isErrorEnabled) logger.error(msg, *args)
    }

    override fun atError(msgFn: () -> String?) {
        if (BaseUtil.ENABLE_ERROR && logger.isErrorEnabled) {
            val msg = msgFn.invoke()
            if (msg != null) logger.error(msg)
        }
    }
}
package naksha.base

import org.slf4j.LoggerFactory

class JvmLogger : PlatformLogger {
    private val logger = LoggerFactory.getLogger("naksha.base")
    override fun debug(msg: String, vararg args: Any?) {
        if (logger.isDebugEnabled) logger.debug(msg, *args)
    }

    override fun atDebug(msgFn: () -> String?) {
        if (logger.isDebugEnabled) {
            val msg = msgFn.invoke()
            if (msg != null) logger.debug(msg)
        }
    }

    override fun info(msg: String, vararg args: Any?) {
        if (logger.isInfoEnabled) logger.info(msg, *args)
    }

    override fun atInfo(msgFn: () -> String?) {
        if (logger.isInfoEnabled) {
            val msg = msgFn.invoke()
            if (msg != null) logger.info(msg)
        }
    }

    override fun warn(msg: String, vararg args: Any?) {
        if (logger.isWarnEnabled) logger.warn(msg, *args)
    }

    override fun atWarn(msgFn: () -> String?) {
        if (logger.isWarnEnabled) {
            val msg = msgFn.invoke()
            if (msg != null) logger.warn(msg)
        }
    }

    override fun error(msg: String, vararg args: Any?) {
        if (logger.isErrorEnabled) logger.error(msg, *args)
    }

    override fun atError(msgFn: () -> String?) {
        if (logger.isErrorEnabled) {
            val msg = msgFn.invoke()
            if (msg != null) logger.error(msg)
        }
    }

}
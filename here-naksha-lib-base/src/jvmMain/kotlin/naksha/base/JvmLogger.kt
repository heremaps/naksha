package naksha.base

import naksha.base.PlatformUtil.PlatformUtil_C.ENABLE_DEBUG
import naksha.base.PlatformUtil.PlatformUtil_C.ENABLE_ERROR
import naksha.base.PlatformUtil.PlatformUtil_C.ENABLE_INFO
import naksha.base.PlatformUtil.PlatformUtil_C.ENABLE_WARN
import org.slf4j.LoggerFactory

class JvmLogger : PlatformLogger {
    private val logger = LoggerFactory.getLogger("naksha.base")
    override fun debug(msg: String, vararg args: Any?) {
        if (ENABLE_DEBUG && logger.isDebugEnabled) logger.debug(msg, *args)
    }

    override fun atDebug(msgFn: () -> String?) {
        if (ENABLE_DEBUG && logger.isDebugEnabled) {
            val msg = msgFn.invoke()
            if (msg != null) logger.debug(msg)
        }
    }

    override fun info(msg: String, vararg args: Any?) {
        if (ENABLE_INFO && logger.isInfoEnabled) logger.info(msg, *args)
    }

    override fun atInfo(msgFn: () -> String?) {
        if (ENABLE_INFO && logger.isInfoEnabled) {
            val msg = msgFn.invoke()
            if (msg != null) logger.info(msg)
        }
    }

    override fun warn(msg: String, vararg args: Any?) {
        if (ENABLE_WARN && logger.isWarnEnabled) logger.warn(msg, *args)
    }

    override fun atWarn(msgFn: () -> String?) {
        if (ENABLE_WARN && logger.isWarnEnabled) {
            val msg = msgFn.invoke()
            if (msg != null) logger.warn(msg)
        }
    }

    override fun error(msg: String, vararg args: Any?) {
        if (ENABLE_ERROR && logger.isErrorEnabled) logger.error(msg, *args)
    }

    override fun atError(msgFn: () -> String?) {
        if (ENABLE_ERROR && logger.isErrorEnabled) {
            val msg = msgFn.invoke()
            if (msg != null) logger.error(msg)
        }
    }

}
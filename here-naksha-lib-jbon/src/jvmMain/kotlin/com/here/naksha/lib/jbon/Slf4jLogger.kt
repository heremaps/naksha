package com.here.naksha.lib.jbon

import org.slf4j.LoggerFactory
import org.slf4j.spi.LoggingEventBuilder

/**
 * The SLF4j logger implementation, default for [JvmEnv].
 */
class Slf4jLogger : ILog{
    private val logger = LoggerFactory.getLogger(Slf4jLogger::class.java)
    private var sb = StringBuilder()

    internal fun log(ctx : LoggingEventBuilder, msg:String, vararg args: Any) {
        val session = JbSession.threadLocal.get()
        sb.setLength(0)
        sb.append("[")
        if (session != null) {
            sb.append(session.streamId)
        } else {
            sb.append("-")
        }
        sb.append("] ")
        sb.append(msg)
        ctx.setMessage(sb.toString())
        for (arg in args) ctx.addArgument(arg)
        ctx.log()
    }

    override fun info(msg: String, vararg args: Any) {
        log(logger.atInfo(), msg, args)
    }

    override fun warn(msg: String, vararg args: Any) {
        log(logger.atWarn(), msg, args)
    }

    override fun error(msg: String, vararg args: Any) {
        log(logger.atError(), msg, args)
    }
}
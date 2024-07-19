@file:Suppress("OPT_IN_USAGE")

package naksha.base

import naksha.base.Platform.PlatformCompanion.toJSON

@Suppress("SENSELESS_COMPARISON")
internal class JsLogger : PlatformLogger {
    // https://plv8.github.io/#-code-plv8-elog-code-
    private val plv8: dynamic = js("typeof plv8=='object' && typeof plv8.log=='function' ? plv8 : null")
    private val _DEBUG: dynamic = if (plv8 != null) js("DEBUG1") else null
    private val _INFO: dynamic = if (plv8 != null) js("INFO") else null
    private val _WARN: dynamic = if (plv8 != null) js("WARNING") else null
    private val _ERROR: dynamic = if (plv8 != null) js("ERROR") else null

    // https://developer.mozilla.org/en-US/docs/Web/API/console
    // TODO: When the argument is a scalar, directly concat, only leave objects as own arguments.
    //       So, we expect that ("Hello {}", "World") returns ["Hello World"] and not ["Hello ", "World"]!
    private fun toString(msg: String, vararg args: Any?): String {
        var r = ""
        var ai = 0
        val msg_arr = js("msg.split(/(?={})/g)")
        var v: dynamic
        var i = 0
        while (i < msg_arr.length.unsafeCast<Int>()) {
            val m = msg_arr[i].replace("%", "%%").unsafeCast<String>()
            if (m.startsWith("{}")) {
                v = args[ai++]
                if (v is Throwable) {
                    r += v.stackTrace.joinToString("\n")
                } else if (v !== null && v !== undefined && (jsTypeOf(v.valueOf()) == "object")) {
                    r += toJSON(v) + m.substring(2)
                } else {
                    r += v + m.substring(2)
                }
            } else {
                r += m;
            }
            i++
        }
        return r
    }

    override fun debug(msg: String, vararg args: Any?) {
        if (!PlatformUtil.ENABLE_DEBUG) return
        // TODO: KotlinCompilerBug:
        //       Report compiler bug, and add FAQ to describe coding hints for JavaScript
        //       this compiles into `toString_1(this, msg, args.slice())`, but if called
        //       from JavaScript, and it implements an @JsExport, the user will call without
        //       arguments, which means that args is undefined, so the compiler need to
        //       adjust for this, and if something is explicitly exported to JavaScript,
        //       it needs to adjust, so at least something like:
        //       `toString_1(this, msg, args ? args.slice() : [])`
        val m = if (args == null || args.size == 0) msg else toString(msg, *args)
        if (plv8 != null) plv8.log(_DEBUG, m) else console.log(m)
    }

    override fun atDebug(msgFn: () -> String?) {
        if (!PlatformUtil.ENABLE_DEBUG) return
        val m = msgFn.invoke()
        if (plv8 != null) plv8.log(_DEBUG, m) else console.log(m)
    }

    override fun info(msg: String, vararg args: Any?) {
        if (!PlatformUtil.ENABLE_INFO) return
        val m = if (args == null || args.size == 0) msg else toString(msg, *args)
        if (plv8 != null) plv8.log(_INFO, m) else console.info(m)
    }

    override fun atInfo(msgFn: () -> String?) {
        if (!PlatformUtil.ENABLE_INFO) return
        val m = msgFn.invoke()
        if (plv8 != null) plv8.log(_INFO, m) else console.info(m)
    }

    override fun warn(msg: String, vararg args: Any?) {
        if (!PlatformUtil.ENABLE_WARN) return
        val m = if (args == null || args.size == 0) msg else toString(msg, *args)
        if (plv8 != null) plv8.log(_WARN, m) else console.info(m)
    }

    override fun atWarn(msgFn: () -> String?) {
        if (!PlatformUtil.ENABLE_WARN) return
        val m = msgFn.invoke()
        if (plv8 != null) plv8.log(_WARN, m) else console.info(m)
    }

    override fun error(msg: String, vararg args: Any?) {
        if (!PlatformUtil.ENABLE_ERROR) return
        val m = if (args == null || args.size == 0) msg else toString(msg, *args)
        if (plv8 != null) plv8.log(_ERROR, m) else console.info(m)
    }

    override fun atError(msgFn: () -> String?) {
        if (!PlatformUtil.ENABLE_ERROR) return
        val m = msgFn.invoke()
        if (plv8 != null) plv8.log(_ERROR, m) else console.info(m)
    }
}
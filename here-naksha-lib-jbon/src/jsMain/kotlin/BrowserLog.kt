package com.here.naksha.lib.jbon

class BrowserLog : ILog {
    override fun info(msg: String, vararg args: Any) {
        js("console.log(msg, args)")
    }

    override fun warn(msg: String, vararg args: Any) {
        js("console.warn(msg, args)")
    }

    override fun error(msg: String, vararg args: Any) {
        js("console.error(msg, args)")
    }
}
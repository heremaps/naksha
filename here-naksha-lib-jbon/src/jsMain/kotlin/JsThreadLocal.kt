@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

/**
 * In Javascript we do not have multiple threads, therefore all threads share the same session instance.
 */
@JsExport
class JsThreadLocal() : IThreadLocal {
    var session: JbSession? = null

    override fun set(session: JbSession?) {
        this.session = session
    }

    override fun get(): JbSession? {
        return session
    }
}
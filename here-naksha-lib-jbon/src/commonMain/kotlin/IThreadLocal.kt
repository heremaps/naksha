@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * The thread local session getter.
 */
@JsExport
interface IThreadLocal {

    /**
     * Sets the session.
     */
    fun set(session : JbSession?)


    /**
     * Returns the thread-local session.
     * @return Thread local session.
     */
    fun get(): JbSession?
}
@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * An API to access the native logging.
 */
@JsExport
interface ILog {
    /**
     * Writes an info.
     */
    fun info(msg:String, vararg args : Any)

    /**
     * Writes a warning.
     */
    fun warn(msg:String, vararg args:Any)

    /**
     * Writes an error.
     */
    fun error(msg:String, vararg args:Any)
}
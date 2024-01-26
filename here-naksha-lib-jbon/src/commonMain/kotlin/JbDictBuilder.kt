@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A dictionary builder, with support to use a global dictionary. In that case, only local dictionaries are creatable.
 */
@JsExport
class JbDictBuilder(val view : IDataView, val global : JbDict? = null) {



    /**
     * Copies all data from this buffer into a new read-only byte-array that represents the final dictionary.
     */
    fun buildLocal() : ByteArray {
        throw NotImplementedError()
    }
}
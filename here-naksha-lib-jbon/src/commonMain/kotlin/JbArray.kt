@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
class JbArray : JbObjectMapper<JbArray>() {
    override fun parseHeader(mandatory: Boolean) {
        TODO("Not yet implemented")
    }

    // TODO: Use two readers, one that moves the entries and one to access the values.
    //       valueReader -> can be used to read values, is always adjusted when seeking
    //       length() : Int -> Returns the amount of entries, requires one seek through (caches length)
    //       first() : Boolean -> same as reset
    //       ok() : Boolean -> if the current entry is available (while (ok()) {... next()})
    //       hasMore() : Boolean -> if a next entry is available
    //       moveTo(index: Int) : Boolean
    //       next() : Boolean
}
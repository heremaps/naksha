@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
class JbMap : JbObjectMapper<JbMap>() {
    override fun parseHeader(mandatory: Boolean) {
        TODO("Not yet implemented")
    }

    // TODO: For map we need another reader for the values, so we can seek in entries
    //       valueReader -> can be used to read values, is always adjusted when seeking
    //       length() : Int -> Returns the amount of entries, requires one seek through (caches length)
    //       first() : Boolean -> same as reset
    //       ok() : Boolean -> if the current entry is available (while (ok()) {... next()})
    //       hasMore() : Boolean -> if a next entry is available
    //       moveTo(index: Int) : Boolean
    //       next() : Boolean
    //       select(key : String) : Boolean -> select the given key or nothing (!ok())
    //       key() : String -> key currently selected
}
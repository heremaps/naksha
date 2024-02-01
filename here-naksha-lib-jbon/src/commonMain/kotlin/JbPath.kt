@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
class JbPath {
    companion object {
        // TODO: Cache the byteArray and the view and when they do not change, do not
        var bytes : ByteArray? = null
        var view : IDataView? = null
        val reader = JbReader()

        fun getBool(binary : ByteArray, path: String, alternative: Boolean = false) : Boolean {
            // TODO: If bytes === binary, then reuse view and reader
            //       Otherwise we need new view and reader needs to map into view using mapView(view, 0)
            // TODO: Map the binary in to a view
            //       Map the view into a default reader
            //       Split the path
            //       Go along the path through the reader and find the target
            //       Return the value if of the type; otherwise alternative
            return alternative
        }
    }
}
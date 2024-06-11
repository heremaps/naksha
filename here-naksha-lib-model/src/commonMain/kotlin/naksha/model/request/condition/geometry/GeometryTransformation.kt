package com.here.naksha.lib.naksha.request

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class GeometryTransformation protected constructor(val childTransformation: GeometryTransformation?) {

    fun hasChildTransformation(): Boolean {
        return childTransformation != null
    }
}

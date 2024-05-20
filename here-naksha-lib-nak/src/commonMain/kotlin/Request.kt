package com.here.naksha.lib.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Base request class.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class Request(
        val noFeature: Boolean = false,
        val noGeometry: Boolean = false,
        val noMeta: Boolean = false,
        val noTags: Boolean = false,
        val resultFilter: Array<IReadRowFilter> = emptyArray()
)
package com.here.naksha.lib.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class ReadRequest(
        val limit: Int = DEFAULT_LIMIT,
        noFeature: Boolean = false,
        noGeometry: Boolean = false,
        noMeta: Boolean = false,
        noTags: Boolean = false,
        resultFilter: Array<IReadRowFilter> = emptyArray()
) : Request(noFeature, noGeometry, noMeta, noTags, resultFilter) {

    companion object {
        var DEFAULT_LIMIT = 100_000
    }
}
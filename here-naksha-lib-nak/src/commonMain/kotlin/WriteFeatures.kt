package com.here.naksha.lib.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class WriteFeatures(
        val collectionId: String,
        rows: Array<WriteOp>,
        noResults: Boolean = false,
        noFeature: Boolean = false,
        noGeometry: Boolean = false,
        noMeta: Boolean = false,
        noTags: Boolean = false,
        // keeps same order of rows in result as was in request.
        restoreInputOrder: Boolean = false,
        resultFilter: Array<IReadRowFilter> = emptyArray()
) : WriteRequest(noResults, rows, noFeature, noGeometry, noMeta, noTags, restoreInputOrder, resultFilter)
package com.here.naksha.lib.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class WriteCollections(
        noResults: Boolean = false,
        rows: Array<WriteOp>,
        noFeature: Boolean = false,
        noGeometry: Boolean = false,
        noMeta: Boolean = false,
        noTags: Boolean = false,
        restoreInputOrder: Boolean = false,
        resultFilter: Array<IReadRowFilter> = emptyArray()
) : WriteRequest(noResults, rows, noFeature, noGeometry, noMeta, noTags, restoreInputOrder, resultFilter)
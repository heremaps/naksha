package com.here.naksha.lib.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class NakWriteCollections(
        noResults: Boolean = false,
        rows: Array<NakWriteRow>,
        noFeature: Boolean = false,
        noGeometry: Boolean = false,
        noMeta: Boolean = false,
        noTags: Boolean = false,
        resultFilter: Array<IReadRowFilter> = emptyArray()
) : NakWriteRequest(noResults, rows, noFeature, noGeometry, noMeta, noTags, resultFilter)
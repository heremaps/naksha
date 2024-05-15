package com.here.naksha.lib.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class NakWriteRequest(
        val noResults: Boolean = false,
        val rows: Array<NakWriteRow>,
        noFeature: Boolean = false,
        noGeometry: Boolean = false,
        noMeta: Boolean = false,
        noTags: Boolean = false,
        resultFilter: Array<IReadRowFilter> = emptyArray()
) : NakRequest(noFeature, noGeometry, noMeta, noTags, resultFilter)
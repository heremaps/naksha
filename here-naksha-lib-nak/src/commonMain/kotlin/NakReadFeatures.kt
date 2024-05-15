package com.here.naksha.lib.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class NakReadFeatures(
        val queryDeleted: Boolean = false,
        val queryHistory: Boolean = false,
        val limitVersions: Int = 1,
        val returnHandle: Boolean = false,
        val orderBy: String? = null,
        val collectionIds: Array<String>,
        limit: Int = DEFAULT_LIMIT,
        noFeature: Boolean = false,
        noGeometry: Boolean = false,
        noMeta: Boolean = false,
        noTags: Boolean = false,
        resultFilter: Array<IReadRowFilter> = emptyArray()
) : NakReadRequest(limit, noFeature, noGeometry, noMeta, noTags, resultFilter)
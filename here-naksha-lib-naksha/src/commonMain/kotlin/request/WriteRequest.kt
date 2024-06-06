package com.here.naksha.lib.naksha.request

import com.here.naksha.lib.naksha.IReadRowFilter
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class WriteRequest(
    /**
     * Write operations to perform.
     * It might have, operations of different types (Insert/Update/etc.) and to different collections (tables).
     */
    val ops: Array<Write>,
    /**
     * When noResults is set, the response will not contain any results (rows). This is the fastest way to perform a write-request.
     * You'll still get information if request succeeded or not.
     */
    val noResults: Boolean = false,
    /**
     * By default (false), response will return rows in same order as were given in request.
     * It's possible to change this behaviour by setting this flag to `true`, in such case response will return rows
     * in order that was most convenient to safely store rows (avoiding locks etc.)
     */
    val allowRandomOrder: Boolean = false,
    noFeature: Boolean = false,
    noGeometry: Boolean = false,
    noMeta: Boolean = false,
    noTags: Boolean = false,
    resultFilter: Array<IReadRowFilter> = emptyArray()
) : Request(noFeature, noGeometry, noMeta, noTags, resultFilter)
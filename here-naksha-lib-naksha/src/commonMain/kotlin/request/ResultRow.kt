package com.here.naksha.lib.naksha.request

import com.here.naksha.lib.base.P_NakshaFeature
import com.here.naksha.lib.base.response.Row
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class ResultRow(
    val op: String,
    val row: Row?, // optional - for retained purged rows
    private var feature: P_NakshaFeature? = null
) {

    fun getFeature(): P_NakshaFeature? {
        if (feature == null) {
            feature = row?.toMemoryModel()
        }
        return feature
    }
}


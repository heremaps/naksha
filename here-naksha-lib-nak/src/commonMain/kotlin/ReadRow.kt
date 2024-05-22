package com.here.naksha.lib.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class ReadRow(
        val op: String,
        val id: String? = null,
        // do we need uuid here? shouldn't it be generated out of Meta?
        val uuid: String? = null,
        val type: String? = null,
        val row: Row?, // optional - for retained purged rows
        private var feature: NakFeature? = null
) {

    fun getFeature(): NakFeature {
        return feature?: throw NotImplementedError("implement conversion from Row to feature")
    }
}


@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
class NakshaException(
        val errNo: String,
        val errMsg: String,
        val id: String? = null,
        val feature: ByteArray? = null,
        val geo: Any? = null,
        val tags: ByteArray? = null,
        val xyz: ByteArray? = null
) : RuntimeException(errMsg)

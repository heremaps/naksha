package com.here.naksha.lib.base

import com.here.naksha.lib.nak.Flags
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class NakRow(
        val id: String? = null,
        val type: String? = null,
        val flags: Flags = Flags(),
        val xyz: ByteArray? = null,
        val feature: ByteArray? = null,
        val geo: ByteArray? = null,
        val geoRef: ByteArray? = null,
        val tags: ByteArray? = null
)
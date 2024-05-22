package com.here.naksha.lib.base

import com.here.naksha.lib.nak.Flags
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class Row(
        val id: String,
        val type: String? = null,
        val flags: Flags = Flags(),
        // do we need uuid here?
        val uuid: String? = null,
        val meta: Metadata? = null,
        val feature: ByteArray? = null,
        val geo: ByteArray? = null,
        val geoRef: ByteArray? = null,
        val tags: ByteArray? = null
)
@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base.response

import com.here.naksha.lib.base.Int64
import kotlin.js.JsExport

/**
 * The XYZ namespace stored in the `@ns:com:here:xyz` property of the [NakFeature].
 */
@JsExport
class Metadata(
    val id: String,
    val txnNext: Int64?,
    val txn: Int64,
    val ptxn: Int64?,
    val fnva1: Int64?,
    val uid: Int?,
    val puid: Int?,
    val version: Int?,
    val geoGrid: Int,
    val flags: Int?,
    val action: Short?,
    val appId: String,
    val author: String?,
    val createdAt: Int64?,
    val updatedAt: Int64,
    val authorTs: Int64?,
    val origin: String?
)
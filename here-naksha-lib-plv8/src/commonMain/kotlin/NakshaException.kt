@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.IMap
import com.here.naksha.lib.jbon.get
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@JsExport
class NakshaException(
        val errNo: String,
        val errMsg: String,
        val id: String? = null,
        val feature: ByteArray? = null,
        val geoType : Short? = null,
        val geo: Any? = null,
        val tags: ByteArray? = null,
        val xyz: ByteArray? = null
) : RuntimeException(errMsg) {
    companion object {
        @JvmStatic
        fun fromRow(errNo:String, errMsg:String, row :IMap) : NakshaException =
                NakshaException(errNo, errMsg, row[COL_ID], row[COL_FEATURE], row[COL_GEO_TYPE], row[COL_GEOMETRY], row[COL_TAGS], row[COL_XYZ])
    }
}

@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8

import com.here.naksha.lib.base.ACTION_CREATE
import com.here.naksha.lib.base.ACTION_DELETE
import com.here.naksha.lib.base.ACTION_UPDATE
import com.here.naksha.lib.base.Metadata
import com.here.naksha.lib.base.NakFeature
import com.here.naksha.lib.base.NakProperties
import com.here.naksha.lib.base.NakXyz
import com.here.naksha.lib.base.Row
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
object ResponseMapper {

    fun fillFeature(featureToFill: NakFeature, row: Row): NakFeature {
        val nakProperties = NakProperties()
        val metadata = row.meta!!
        nakProperties.setXyz(toXyzNs(metadata))
        featureToFill.setProperties(nakProperties)
        return featureToFill
    }

    fun toXyzNs(metadata: Metadata): NakXyz {
        val nakXyz = NakXyz()
        nakXyz.setPtxn(metadata.ptxn)
        nakXyz.setPuid(metadata.puid)
        //nakXyz.setTags() // TODO fixme
        nakXyz.setUid(metadata.uid)
        nakXyz.setAction(actionAsString(metadata.action?.toInt()))
        nakXyz.setAppId(metadata.appId)
        nakXyz.setAuthor(metadata.author)
        nakXyz.setAuthorTs(metadata.authorTs ?: metadata.updatedAt)
        nakXyz.setCreatedAt(metadata.createdAt ?: metadata.updatedAt)
        nakXyz.setUpdatedAt(metadata.updatedAt)
        nakXyz.setTxnNext(metadata.txnNext)
        nakXyz.setVersion(metadata.version)
        return nakXyz
    }

    fun actionAsString(action: Int?): String? = when (action) {
        ACTION_CREATE -> "CREATE"
        ACTION_UPDATE -> "UPDATE"
        ACTION_DELETE -> "DELETE"
        else -> null
    }
}
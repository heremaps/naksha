@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8

import com.here.naksha.lib.base.ACTION_CREATE
import com.here.naksha.lib.base.ACTION_DELETE
import com.here.naksha.lib.base.ACTION_UPDATE
import com.here.naksha.lib.base.Metadata
import com.here.naksha.lib.base.NakFeature
import com.here.naksha.lib.base.NakProperties
import com.here.naksha.lib.base.NakResponse
import com.here.naksha.lib.base.NakSuccessResponse
import com.here.naksha.lib.base.NakXyz
import com.here.naksha.lib.base.ReadRow
import com.here.naksha.lib.base.Row
import com.here.naksha.lib.base.XYZ_EXEC_CREATED
import com.here.naksha.lib.base.XYZ_EXEC_DELETED
import com.here.naksha.lib.base.XYZ_EXEC_UPDATED
import com.here.naksha.lib.jbon.IMap
import com.here.naksha.lib.jbon.get
import com.here.naksha.lib.nak.Flags
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
        nakXyz.setTxn(metadata.txn)
        nakXyz.setGeoGrid(metadata.geoGrid)
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

    fun actionAsOp(action: Int): String = when (action) {
        ACTION_CREATE -> XYZ_EXEC_CREATED
        ACTION_UPDATE -> XYZ_EXEC_UPDATED
        ACTION_DELETE -> XYZ_EXEC_DELETED
        else -> throw NotImplementedError("Unknown action $action")
    }

    fun map(rows: Array<IMap>): NakResponse {
        return NakSuccessResponse(
            handle = null, // TODO implement me
            rows = rows.map(::toReadRow).toTypedArray()
        )
    }

    fun toReadRow(row: IMap): ReadRow {
        return ReadRow(
            op = actionAsOp(row[COL_ACTION]!!),
            id = row[COL_ID],
            uuid = null, // TODO or FIXME maybe we don't need uuid here
            type = row[COL_TYPE],
            feature = null, // lazy transformed
            row = Row(
                id = row[COL_ID]!!,
                type = row[COL_TYPE],
                flags = Flags(row[COL_FLAGS]),
                uuid = null, // TODO or FIXME maybe we don't need uuid here
                meta = Metadata(
                    id = row[COL_ID]!!,
                    txnNext = row[COL_TXN_NEXT],
                    txn = row[COL_TXN]!!,
                    ptxn = row[COL_PTXN],
                    fnva1 = null, // TODO implement me
                    uid = row[COL_UID],
                    puid = row[COL_PUID],
                    version = row[COL_VERSION],
                    geoGrid = row[COL_GEO_GRID]!!,
                    flags = row[COL_FLAGS],
                    action = row[COL_ACTION],
                    appId = row[COL_APP_ID]!!,
                    author = row[COL_AUTHOR],
                    createdAt = row[COL_CREATED_AT],
                    updatedAt = row[COL_UPDATE_AT]!!,
                    authorTs = row[COL_AUTHOR_TS],
                ),
                feature = row[COL_FEATURE],
                geo = row[COL_GEOMETRY],
                geoRef = row[COL_GEO_REF],
                tags = row[COL_TAGS],
            )
        )
    }
}
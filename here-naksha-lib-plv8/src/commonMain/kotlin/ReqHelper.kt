@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8

import com.here.naksha.lib.base.Base
import com.here.naksha.lib.base.NakRow
import com.here.naksha.lib.base.NakWriteCollections
import com.here.naksha.lib.base.NakWriteFeatures
import com.here.naksha.lib.base.NakWriteRow
import com.here.naksha.lib.jbon.XYZ_OP_CREATE
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
object ReqHelper {

    fun prepareCollectionReqCreate(id: String, collectionBytes: ByteArray): NakWriteCollections = prepareCollectionReq(XYZ_OP_CREATE, id, collectionBytes)

    fun prepareCollectionReq(action: Int, id: String, collectionBytes: ByteArray): NakWriteCollections {
        val collectionWriteReq = NakWriteCollections()
        val row = NakRow()
        row.setFeature(collectionBytes)
        val writeOp = NakWriteRow.fromRow(action, row)
        writeOp.setId(id)
        collectionWriteReq.setRows(Base.newArray(writeOp))
        return collectionWriteReq
    }

    fun prepareFeatureReq(action: Int, collectionId: String, featureId: String? = null, featureBytes: ByteArray? = null): NakWriteFeatures {
        val writeOp = prepareOperation(action, featureId, featureBytes)
        return prepareFeatureReqForOperations(collectionId, writeOp)
    }

    fun prepareFeatureReqForOperations(collectionId: String, vararg operations: NakWriteRow): NakWriteFeatures {
        val nakWriteFeatures = NakWriteFeatures(collectionId)
        nakWriteFeatures.setRows(Base.newArray(*operations))
        return nakWriteFeatures
    }

    fun prepareOperation(action: Int, featureId: String? = null, featureBytes: ByteArray? = null, geo: ByteArray? = null, tags: ByteArray? = null, flags: Int? = null): NakWriteRow {
        val row = NakRow()
        row.setFeature(featureBytes)
        row.setGeo(geo)
        row.setTags(tags)
        row.setFlags(flags)
        val writeOp = NakWriteRow.fromRow(action, row)
        writeOp.setId(featureId)
        return writeOp
    }
}
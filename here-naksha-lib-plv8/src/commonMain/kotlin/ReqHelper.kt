@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8

import com.here.naksha.lib.base.NakCollection
import com.here.naksha.lib.base.NakRow
import com.here.naksha.lib.base.NakWriteCollections
import com.here.naksha.lib.base.NakWriteFeatures
import com.here.naksha.lib.base.NakWriteRow
import com.here.naksha.lib.jbon.XYZ_OP_CREATE
import com.here.naksha.lib.nak.Flags
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
object ReqHelper {

    fun prepareCollectionReqCreate(id: String, collectionBytes: ByteArray): NakWriteCollections = prepareCollectionReq(XYZ_OP_CREATE, id, collectionBytes, null)

    fun prepareCollectionReqCreateFromFeature(id: String, collectionFeature: NakCollection): NakWriteCollections {
        return prepareCollectionReq(XYZ_OP_CREATE, id, null, collectionFeature)
    }


    fun prepareCollectionReq(action: Int, id: String, collectionBytes: ByteArray? = null, collectionFeature: NakCollection? = null): NakWriteCollections {
        check(collectionFeature != null || collectionBytes != null)

        val row = NakRow(feature = collectionBytes)
        val writeOp = NakWriteRow(action, row = row, id = id, feature = collectionFeature)
        val collectionWriteReq = NakWriteCollections(rows = arrayOf(writeOp))
        return collectionWriteReq
    }

    fun prepareFeatureReq(action: Int, collectionId: String, featureId: String? = null, featureBytes: ByteArray? = null): NakWriteFeatures {
        val writeOp = prepareOperation(action, featureId, featureBytes)
        return prepareFeatureReqForOperations(collectionId, writeOp)
    }

    fun prepareFeatureReqForOperations(collectionId: String, vararg operations: NakWriteRow): NakWriteFeatures {
        val nakWriteFeatures = NakWriteFeatures(collectionId, rows = arrayOf(*operations))
        return nakWriteFeatures
    }

    fun prepareOperation(action: Int, featureId: String? = null, featureBytes: ByteArray? = null, geo: ByteArray? = null, tags: ByteArray? = null, flags: Int? = null): NakWriteRow {
        val row = NakRow(
                feature = featureBytes,
                geo = geo,
                tags = tags,
                flags = Flags(flags)
        )
        return NakWriteRow(action, row = row, id = featureId)
    }
}
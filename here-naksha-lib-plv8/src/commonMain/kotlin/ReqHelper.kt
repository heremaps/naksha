@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8

import com.here.naksha.lib.base.DeleteFeature
import com.here.naksha.lib.base.InsertFeature
import com.here.naksha.lib.base.InsertRow
import com.here.naksha.lib.base.NakCollection
import com.here.naksha.lib.base.Row
import com.here.naksha.lib.base.WriteCollections
import com.here.naksha.lib.base.WriteFeatures
import com.here.naksha.lib.base.WriteOp
import com.here.naksha.lib.nak.Flags
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
object ReqHelper {

    fun prepareCollectionReqCreate(id: String, collectionBytes: ByteArray): WriteCollections = prepareCreateCollectionReq(id, collectionBytes, null)

    fun prepareCollectionReqCreateFromFeature(id: String, collectionFeature: NakCollection): WriteCollections {
        return prepareCreateCollectionReq(id, null, collectionFeature)
    }

    fun deleteCollection(id: String, uuid: String? = null): WriteCollections {
        return WriteCollections(rows = arrayOf(DeleteFeature(collectionId = NKC_TABLE, id = id, uuid = uuid)))
    }


    fun prepareCreateCollectionReq(id: String, collectionBytes: ByteArray? = null, collectionFeature: NakCollection? = null, flags: Flags = Flags()): WriteCollections {
        check(collectionFeature != null || collectionBytes != null)


        val writeOp = if (collectionBytes != null) {
            InsertRow(row = Row(feature = collectionBytes, id = id), collectionId = id)
        } else {
            InsertFeature(collectionId = id, feature = collectionFeature!!, flags = flags)
        }
        val collectionWriteReq = WriteCollections(rows = arrayOf(writeOp))
        return collectionWriteReq
    }

    fun prepareFeatureInsertReq(collectionId: String, featureId: String, featureBytes: ByteArray? = null): WriteFeatures {
        val writeOp = prepareInsertOperation(collectionId, featureId, featureBytes)
        return prepareFeatureReqForOperations(collectionId, writeOp)
    }

    fun prepareFeatureReqForOperations(collectionId: String, vararg operations: WriteOp): WriteFeatures {
        val nakWriteFeatures = WriteFeatures(collectionId, rows = arrayOf(*operations))
        return nakWriteFeatures
    }

    fun prepareInsertOperation(collectionId: String, featureId: String, featureBytes: ByteArray? = null, geo: ByteArray? = null, tags: ByteArray? = null, flags: Int? = null): InsertRow {
        val row = Row(
                id = featureId,
                feature = featureBytes,
                geo = geo,
                tags = tags,
                flags = Flags(flags)
        )
        return InsertRow(row = row, collectionId =  collectionId)
    }
}
@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.*
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * When a function returns a table, then PLV8 will create a
 * [tuple-store](https://github.com/postgres/postgres/blob/master/src/backend/utils/sort/tuplestore.c) for this.
 * To return the rows, the function has to invoke `plv8.return_next(object)`. To optimize performance,
 * we will directly ask to use native objects here. In PostgresQL this design allows to read results while
 * they are produced.
 *
 * For the PostgresQL implementation, this interface does nothing, but redirecting calls to `plv8.return_next(object)`.,
 * For the JVM implementation it will create an in-memory virtual tuple-store. The function, e.g. [NakshaSession.writeFeatures],
 * will then return the table so that the results can be verified.
 */
@Suppress("DuplicatedCode")
@JsExport
interface ITable {
    /**
     * Returns a new row.
     * @param ret The return row.
     */
    fun returnNext(ret: IMap)

    /**
     * Returns a Naksha default row for a successful operation.
     */
    fun returnOk(op: String, id: String, xyz: ByteArray, tags: ByteArray?, feature: ByteArray? = null, geoType: Short? = null, geo: Any? = null) {
        val map = Jb.map.newMap()
        map[RET_OP] = op
        map[RET_ID] = id
        map[RET_XYZ] = xyz
        map[RET_TAGS] = tags
        map[RET_GEO_TYPE] = geoType
        map[RET_GEOMETRY] = geo
        map[RET_FEATURE] = feature
        map[RET_ERR_NO] = null
        map[RET_ERR_MSG] = null
        returnNext(map)
    }

    /**
     * Returns a Naksha default row for a successful operation.
     * @param op The executed operation.
     * @param row The database row to return, will be mapped to the return row.
     */
    fun returnRow(op: String, row:IMap) {
        val map = Jb.map.newMap()
        map[RET_OP] = op
        map[RET_ID] = row[COL_ID]
        map[RET_XYZ] = row[COL_XYZ]
        map[RET_TAGS] = row[COL_TAGS]
        map[RET_GEO_TYPE] = row[COL_GEO_TYPE]
        map[RET_GEOMETRY] = row[COL_GEOMETRY]
        map[RET_FEATURE] = row[COL_FEATURE]
        map[RET_ERR_NO] = null
        map[RET_ERR_MSG] = null
        returnNext(map)
    }

    /**
     * Returns a Naksha default row for a failure.
     */
    fun returnException(e: NakshaException) {
        returnErr(e.errNo, e.errMsg, e.id, e.xyz, e.tags, e.feature, e.geoType, e.geo)
    }

    /**
     * Returns a Naksha default row for a failure.
     */
    fun returnErr(errNo: String, errMsg: String, id: String? = null, xyz: ByteArray? = null, tags: ByteArray? = null, feature: ByteArray? = null, geoType: Short? = null, geo: Any? = null) {
        val map = Jb.map.newMap()
        map["op"] = XYZ_EXECUTED_ERROR
        map["id"] = id
        map["xyz"] = xyz
        map["tags"] = tags
        map["geo_type"] = geoType ?: GEO_TYPE_NULL
        map["geo"] = geo
        map["feature"] = feature
        map["err_no"] = errNo
        map["err_msg"] = errMsg
        val session = NakshaSession.get()
        session.errNo = errNo
        session.errMsg = errMsg
        returnNext(map)
    }
}
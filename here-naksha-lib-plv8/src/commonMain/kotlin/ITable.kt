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
     * @param row The native map of the row.
     */
    fun returnNext(row: IMap)

    /**
     * Returns a Naksha default row for a successful operation.
     */
    fun returnOpOk(op: String, id: String, xyz: ByteArray, tags: ByteArray?, feature: ByteArray? = null, geo: Any? = null) {
        val map = Jb.map.newMap()
        map["op"] = op
        map["id"] = id
        map["xyz"] = xyz
        map["tags"] = tags
        map["geo"] = geo
        map["feature"] = feature
        map["err_no"] = null
        map["err_msg"] = null
        returnNext(map)
    }

    /**
     * Returns a Naksha default row for a failure.
     */
    fun returnException(e: NakshaException) {
        returnOpErr(e.errNo, e.errMsg, e.id, e.xyz, e.tags, e.feature, e.geo)
    }

    /**
     * Returns a Naksha default row for a failure.
     */
    fun returnOpErr(errNo: String, errMsg: String, id: String? = null, xyz: ByteArray? = null, tags: ByteArray? = null, feature: ByteArray? = null, geo: Any? = null) {
        val map = Jb.map.newMap()
        map["op"] = XYZ_EXECUTED_ERROR
        map["id"] = id
        map["xyz"] = xyz
        map["tags"] = tags
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
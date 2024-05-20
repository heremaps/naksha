@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8

import com.here.naksha.lib.base.XYZ_EXEC_CREATED
import com.here.naksha.lib.base.XYZ_EXEC_DELETED
import com.here.naksha.lib.base.XYZ_EXEC_ERROR
import com.here.naksha.lib.base.XYZ_EXEC_PURGED
import com.here.naksha.lib.base.XYZ_EXEC_READ
import com.here.naksha.lib.base.XYZ_EXEC_RETAINED
import com.here.naksha.lib.base.XYZ_EXEC_UPDATED
import com.here.naksha.lib.jbon.*
import com.here.naksha.lib.nak.Flags
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
     * Returns a success result for an operation, the _errNo_ and _errMsg_ will be always _null_.
     *
     * @param op The CRUD operation that has been executed, should be [XYZ_EXEC_CREATED], [XYZ_EXEC_READ], [XYZ_EXEC_UPDATED],
     * [XYZ_EXEC_DELETED], [XYZ_EXEC_PURGED] or [XYZ_EXEC_RETAINED].
     * @param id The identifier of the feature that was modified.
     * @param xyz The new XYZ namespace; _null_ if [XYZ_EXEC_RETAINED].
     * @param tags The new tags; _null_ if the operation was [XYZ_EXEC_CREATED], [XYZ_EXEC_UPDATED] or [XYZ_EXEC_RETAINED].
     * @param feature The new feature; _null_ if the operation was [XYZ_EXEC_CREATED], [XYZ_EXEC_UPDATED] or [XYZ_EXEC_RETAINED].
     * @param flags The geometry type; _null_ if the operation was [XYZ_EXEC_CREATED], [XYZ_EXEC_UPDATED] or [XYZ_EXEC_RETAINED].
     * @param geo The geometry bytes; _null_ if the operation was [XYZ_EXEC_CREATED], [XYZ_EXEC_UPDATED] or [XYZ_EXEC_RETAINED].
     */
    fun returnOk(op: String, id: String, xyz: ByteArray?, tags: ByteArray? = null, feature: ByteArray? = null, flags: Int? = null, geo: Any? = null) {
        val map = Jb.map.newMap()
        map[RET_OP] = op
        map[RET_ID] = id
        map[RET_XYZ] = xyz
        map[RET_TAGS] = tags
        map[RET_FLAGS] = flags
        map[RET_GEOMETRY] = geo
        map[RET_FEATURE] = feature
        map[RET_ERR_NO] = null
        map[RET_ERR_MSG] = null
        returnNext(map)
    }

    /**
     * Returns a successful create (insert).
     * @param id The identifier of the feature that was modified.
     * @param xyz The new XYZ namespace produced for the new record.
     */
    fun returnCreated(id: String, xyz: ByteArray) {
        returnOk(XYZ_EXEC_CREATED, id, xyz)
    }

    /**
     * Returns a successful read.
     * @param row The database row that was read.
     */
    fun returnRead(row: IMap) {
        returnRow(XYZ_EXEC_READ, row)
    }

    /**
     * Returns a successful update.
     * @param id The identifier of the feature that was modified.
     * @param xyz The new XYZ namespace produced for the new record.
     */
    fun returnUpdated(id: String, xyz: ByteArray) {
        returnOk(XYZ_EXEC_UPDATED, id, xyz)
    }

    /**
     * Returns a successful deletion.
     * @param row The database row that was deleted.
     * @param xyz The new XYZ namespace produced for deleted record.
     */
    fun returnDeleted(row: IMap, xyz: ByteArray? = null) {
        returnRow(XYZ_EXEC_DELETED, row, xyz)
    }

    /**
     * Returns a successful purge.
     * @param row The database row that was purged.
     * @param xyz The new XYZ namespace produced for purged record.
     */
    fun returnPurged(row: IMap, xyz: ByteArray? = null) {
        returnRow(XYZ_EXEC_PURGED, row, xyz)
    }

    /**
     * Returns that the state of the feature did not change, called for [XYZ_OP_DELETE] or [XYZ_OP_PURGE], when the
     * feature does not exist.
     * @param id The identifier of the feature.
     */
    fun returnRetained(id: String) {
        returnOk(XYZ_EXEC_RETAINED, id, null, null, null, null, null)
    }

    /**
     * Returns an error for an operation. In this case the operation (_op_) will always be [XYZ_EXEC_ERROR].
     * @param errNo The machine-readable error number.
     * @param errMsg The human-readable error message.
     * @param id The identifier of the feature that raised the error; may be _null_, if the error is that we do not have an identifier.
     * @param xyz The current value from the database that caused the error; _null_ if the cause is that no such feature exists.
     * @param tags The current value from the database that caused the error; _null_ if the cause is that no such feature exists.
     * @param feature The current value from the database that caused the error; _null_ if the cause is that no such feature exists.
     * @param flags The current value from the database that caused the error; _null_ if the cause is that no such feature exists.
     * @param geo The current value from the database that caused the error; _null_ if the cause is that no such feature exists.
     */
    fun returnErr(errNo: String, errMsg: String, id: String? = null, xyz: ByteArray? = null, tags: ByteArray? = null, feature: ByteArray? = null, flags: Int? = null, geo: Any? = null) {
        val map = Jb.map.newMap()
        map[RET_OP] = XYZ_EXEC_ERROR
        map[RET_ID] = id
        map[RET_XYZ] = xyz
        map[RET_TAGS] = tags
        map[RET_FLAGS] = flags ?: Flags()
        map[RET_GEOMETRY] = geo
        map[RET_FEATURE] = feature
        map[RET_ERR_NO] = errNo
        map[RET_ERR_MSG] = errMsg
        val session = NakshaSession.get()
        session.errNo = errNo
        session.errMsg = errMsg
        returnNext(map)
    }

    /**
     * When we have a row from the database that should be returned. Normally only done for [XYZ_EXEC_READ], [XYZ_EXEC_DELETED],
     * [XYZ_EXEC_PURGED] or [XYZ_EXEC_RETAINED].
     * @param op The executed operation.
     * @param row The database row to return, will be mapped to the return row.
     */
    fun returnRow(op: String, row: IMap, xyz: ByteArray? = null) {
        val map = Jb.map.newMap()
        map[RET_OP] = op
        map[RET_ID] = row[COL_ID]
        map[RET_XYZ] = xyz
        map[RET_TAGS] = row[COL_TAGS]
        map[RET_FLAGS] = row[COL_FLAGS]
        map[RET_GEOMETRY] = row[COL_GEOMETRY]
        map[RET_FEATURE] = row[COL_FEATURE]
        map[RET_ERR_NO] = null
        map[RET_ERR_MSG] = null
        returnNext(map)
    }

    /**
     * Returns an error using the given exception.
     * @param e The exception to use to generate the error.
     */
    fun returnException(e: NakshaException) {
        returnErr(e.errNo, e.errMsg, e.id, e.xyz, e.tags, e.feature, e.flags, e.geo)
    }

}
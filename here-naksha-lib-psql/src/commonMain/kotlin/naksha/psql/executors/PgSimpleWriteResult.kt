package naksha.psql.executors

import naksha.base.Int64
import naksha.model.IRowIdArray
import naksha.model.IStorage
import naksha.model.RowId
import naksha.model.request.ResultRowList

internal class PgSimpleWriteResult : IRowIdArray {
    override val size: Int
        get() = TODO("Not yet implemented")

    override fun getTxn(index: Int): Int64? {
        TODO("Not yet implemented")
    }

    override fun getUid(index: Int): Int? {
        TODO("Not yet implemented")
    }

    override fun getFlags(index: Int): Int? {
        TODO("Not yet implemented")
    }

    override fun getCollection(index: Int): String? {
        TODO("Not yet implemented")
    }

    override fun getRowId(index: Int): RowId? {
        TODO("Not yet implemented")
    }

    override fun toResultRowList(storage: IStorage, map: String): ResultRowList {
        TODO("Not yet implemented")
    }
}
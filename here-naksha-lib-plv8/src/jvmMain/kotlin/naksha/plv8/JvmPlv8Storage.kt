package com.here.naksha.lib.plv8.naksha.plv8

import naksha.base.Int64
import naksha.model.*
import naksha.model.response.Row

class JvmPlv8Storage(private val id: String) : IStorage {
    override fun id(): String = id

    override fun initStorage() {
        TODO("Not yet implemented")
    }

    override fun convertRowToFeature(row: Row): NakshaFeatureProxy? {
        TODO("Not yet implemented")
    }

    override fun convertFeatureToRow(feature: NakshaFeatureProxy): Row {
        TODO("Not yet implemented")
    }

    override fun enterLock(id: String, waitMillis: Int64): ILock {
        TODO("Not yet implemented")
    }

    override fun openReadSession(nakshaContext: NakshaContext, useMaster: Boolean): IReadSession {
        TODO("Not yet implemented")
    }

    override fun openWriteSession(nakshaContext: NakshaContext, useMaster: Boolean): IWriteSession {
        TODO("Not yet implemented")
    }


}
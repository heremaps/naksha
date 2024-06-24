package com.here.naksha.lib.plv8.naksha.plv8

import com.here.naksha.lib.plv8.JvmPlv8Sql
import naksha.base.Int64
import naksha.jbon.*
import naksha.model.*
import naksha.model.response.Row
import naksha.plv8.NakshaSession
import java.sql.Connection


class JvmPlv8Storage(private val id: String, private val connection: Connection, val schema:String) : IStorage {
    override fun id(): String = id

    override fun initStorage() {
        JvmPlv8Env(this).install(connection, 0, schema, id, appName = "fixme")
    }

    override fun convertRowToFeature(row: Row): NakshaFeatureProxy? {
        return if (row.feature != null) {
            // FIXME
            val featureReader = JbMapFeature(JbDictManager()).mapBytes(row.feature!!).reader
            JbMap().mapReader(featureReader).toIMap().proxy(NakshaFeatureProxy::class)
        } else {
            null
        }
    }

    override fun convertFeatureToRow(feature: NakshaFeatureProxy): Row {
        return Row(
            storage = this,
            flags = Flags.DEFAULT_FLAGS,
            id = feature.id,
            feature = XyzBuilder().buildFeatureFromMap(feature), // FIXME split feature to geo etc
            geoRef = null,
            geo = null,
            tags = null
        )
    }

    override fun enterLock(id: String, waitMillis: Int64): ILock {
        TODO("Not yet implemented")
    }

    override fun openReadSession(nakshaContext: NakshaContext, useMaster: Boolean): IReadSession {
        TODO("Not yet implemented")
    }

    override fun openWriteSession(nakshaContext: NakshaContext, useMaster: Boolean): IWriteSession {
        return JvmPlv8WriteSession(
            connection,
            this,
            nakshaContext,
            stmtTimeout = 2000,
            lockTimeout = 2000
        )
    }


}
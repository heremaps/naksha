package com.here.naksha.lib.plv8.naksha.plv8

import naksha.base.Int64
import naksha.base.PlatformObject
import naksha.jbon.*
import naksha.model.*
import naksha.model.response.Row
import java.sql.Connection


class JvmPlv8Storage(private val id: String, private val connection: Connection, val schema:String) : IStorage {
    override fun id(): String = id

    override fun initStorage(params: Map<String, *>?) {
        JvmPlv8Env(this).install(connection, 0, schema, id, appName = "fixme")
    }

    override fun convertRowToFeature(row: Row): NakshaFeatureProxy {
        return if (row.feature != null) {
            // TODO: FIXME, we need the XYZ namespace
            val featureReader = JbMapFeature(JbDictManager()).mapBytes(row.feature!!).reader
            val feature = JbMap().mapReader(featureReader).toIMap().proxy(NakshaFeatureProxy::class)
            feature
        } else {
            TODO("We will always have at least the id, which is formally enough to generate an empty feature!")
        }
    }

    override fun convertFeatureToRow(feature: PlatformObject): Row {
        val nakshaFeature = feature.proxy(NakshaFeatureProxy::class)
        return Row(
            storage = this,
            flags = Flags.DEFAULT_FLAGS,
            id = nakshaFeature.id,
            feature = XyzBuilder().buildFeatureFromMap(nakshaFeature), // FIXME split feature to geo etc
            geoRef = null,
            geo = null,
            tags = null
        )
    }

    override fun enterLock(id: String, waitMillis: Int64): ILock {
        TODO("Not yet implemented")
    }

    override fun newReadSession(context: NakshaContext, useMaster: Boolean): IReadSession {
        TODO("Not yet implemented")
    }

    override fun newWriteSession(context: NakshaContext): IWriteSession {
        return JvmPlv8WriteSession(
            connection,
            this,
            context,
            stmtTimeout = 2000,
            lockTimeout = 2000
        )
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    override fun dictManager(nakshaContext: NakshaContext): IDictManager {
        TODO("Not yet implemented")
    }
}
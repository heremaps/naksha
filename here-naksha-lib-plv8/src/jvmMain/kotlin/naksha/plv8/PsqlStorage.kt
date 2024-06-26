package com.here.naksha.lib.plv8.naksha.plv8

import naksha.base.Int64
import naksha.base.PlatformObject
import naksha.jbon.*
import naksha.model.*
import naksha.model.response.Row

/**
 * The Java implementation of the [IStorage] interface. This storage class is extended in `here-naksha-storage-psql`,
 * which has a `PsqlStoragePlugin` class, which implements the `Plugin` contract (internal contract in _Naksha-Hub_) and
 * makes the storage available to **Naksha-Hub** as storage plugin. It parses the configuration from feature properties
 * given, and calls this constructor.
 * @constructor Creates a new PSQL storage.
 * @property id the identifier of the storage.
 * @property pgCluster the PostgresQL instances used by this storage.
 * @property schema the schema to use.
 */
open class PsqlStorage(val id: String, val pgCluster: PsqlCluster, val schema: String) : IStorage {

    override fun id(): String = id

    override fun initStorage(params: Map<String, *>?) {
        val conn = pgCluster.getConnection(PsqlConnectOptions(false))
        conn.use {
            JvmPlv8Env(this).install(conn, 0, schema, id, appName = "fixme")
        }
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
        return PsqlWriteSession(this, PsqlConnectOptions(true), context)
    }

    override fun newWriteSession(context: NakshaContext): IWriteSession {
        return PsqlWriteSession(this, PsqlConnectOptions(false), context)
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    override fun dictManager(nakshaContext: NakshaContext): IDictManager {
        TODO("Not yet implemented")
    }
}
package com.here.naksha.cli.streaming

import naksha.base.Id
import naksha.base.JvmMap
import naksha.base.Platform
import naksha.base.Version
import naksha.base.proxy
import naksha.geo.GeoUtil
import naksha.model.IStorage
import naksha.model.IStreamSession
import naksha.model.SessionOptions
import naksha.model.objects.NakshaFeature
import naksha.model.streaming.Stream
import naksha.model.streaming.StreamChunk
import naksha.model.streaming.StreamRequest
import naksha.model.streaming.StreamTuple
import naksha.psql.PgConfig
import naksha.psql.PsqlConnection
import naksha.psql.PsqlInstance

class StreamReadSessionV2(
    override val storage: IStorage,
    override var socketTimeout: Int,
    override var stmtTimeout: Int,
    override var lockTimeout: Int,
    override val options: SessionOptions
) : IStreamSession {
    override val mayRead: Boolean
        get() = true

    /**
     * The connection to the database, opened in read-only mode.
     * @since 3.0.0
     */
    val connection: PsqlConnection

    init {
        val master = PsqlInstance(Platform.proxy(storage.config.properties, PgConfig::class).master)

        connection = master.openConnection(
            options = SessionOptions(),
            readOnly = true,
            init = null
        )
    }

    companion object {
        const val VERSION_COLUMN_SQL = "i"
    }

    override fun read(request: StreamRequest): Stream {
        val stream = StreamV2(this, request)
        val whereClause = StringBuilder()
        if (request.minVersion > 0) {
            if (whereClause.isEmpty()) {
                whereClause.append(" WHERE $VERSION_COLUMN_SQL >= ${request.minVersion}")
            }
            else {
                whereClause.append(" AND $VERSION_COLUMN_SQL >= ${request.minVersion}")
            }
        }
        if (request.version < Version.HEAD.number) {
            if (whereClause.isEmpty()) {
                whereClause.append(" WHERE $VERSION_COLUMN_SQL <= ${request.version}")
            }
            else {
                whereClause.append(" AND $VERSION_COLUMN_SQL <= ${request.version}")
            }
        }
        val sql = "SELECT jsondata, ST_AsEWKB(geo) as geo FROM ${request.catalogId}.${request.collectionId}$whereClause;"
        val cursor = connection.execute(sql)
        val tuples = ArrayList<StreamTuple>()
        while (cursor.next()) {
            val jsonData : JvmMap = cursor["jsondata"]
            val geoData : ByteArray = cursor["geo"]
            val feature = jsonData.proxy(NakshaFeature::class) //TODO might fail here due to different version layout
            feature.geometry = GeoUtil.fromEWKB(geoData)
            val id = Id(feature.id)
            val streamTuple = StreamTuple(
                id = id,
                feature = feature.platformObject())
            tuples.add(streamTuple)
        }
        stream.addChunk(StreamChunk(stream, tuples.toTypedArray()))
        return stream
    }

    override val mayWrite: Boolean
        get() = false

    override fun write(chunk: StreamChunk): Boolean {
        throw UnsupportedOperationException("Write is not supported in StreamReadSessionV2")
    }

    override fun isClosed(): Boolean {
        return connection.isClosed()
    }

    override fun close() {
        connection.close()
    }
}
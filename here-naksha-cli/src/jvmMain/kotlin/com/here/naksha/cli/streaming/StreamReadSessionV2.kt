package com.here.naksha.cli.streaming

import naksha.base.Id
import naksha.base.JvmMap
import naksha.base.Platform
import naksha.base.Version
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
        /**
         * An ever-increasing sequence number <collection>i_seq that is used as the last component of feature UUIDs, also available in a dedicated postgres column and indexed.
         */
        const val VERSION_COLUMN_SQL = "i"
    }

    private fun queryHead(request: StreamRequest, startingVersion: Long = request.minVersion): String {
        val whereClause = StringBuilder(" WHERE $VERSION_COLUMN_SQL >= $startingVersion")
        if (request.version < Version.HEAD.number) {
            whereClause.append(" AND $VERSION_COLUMN_SQL <= ${request.version}")
        }
        return "SELECT jsondata, ST_AsEWKB(geo) as geo FROM ${request.catalogId}.${request.collectionId}$whereClause ORDER BY $VERSION_COLUMN_SQL ASC LIMIT ${request.chunkSize};"
    }

    override fun read(request: StreamRequest): Stream {
        val stream = StreamV2(this, request)
        val sql = queryHead(request)
        val cursor = connection.execute(sql)
        val tuples = ArrayList<StreamTuple>()
        while (cursor.next()) {
            val jsonData : JvmMap = cursor["jsondata"]
            val geoData : ByteArray = cursor["geo"]
            val feature = jsonData.proxy(NakshaFeature::class)
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
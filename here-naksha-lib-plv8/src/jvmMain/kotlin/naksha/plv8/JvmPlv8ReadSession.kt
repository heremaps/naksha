package com.here.naksha.lib.plv8.naksha.plv8

import com.here.naksha.lib.plv8.JvmPlv8Sql
import naksha.model.IReadSession
import naksha.model.NakshaContext
import naksha.model.request.ReadRequest
import naksha.model.request.Request
import naksha.model.request.ResultRow
import naksha.model.response.Response
import naksha.plv8.NakshaSession
import java.sql.Connection

open class JvmPlv8ReadSession(
    var connection: Connection,
    val storage: JvmPlv8Storage,
    context: NakshaContext,
    stmtTimeout: Int,
    lockTimeout: Int
) : JvmPlv8Session(context, stmtTimeout, lockTimeout), IReadSession {

    protected val nakshaSession: NakshaSession = NakshaSession(
        sql = JvmPlv8Sql(connection),
        schema = "FIXME", // FIXME
        storage = storage,
        appName = "FIXME", // FIXME
        streamId = "FIXME", // FIXME
        appId = context.appId,
        author = context.author
    )

    override fun execute(request: Request): Response {
        return when (request) {
            // TODO implement read
            is ReadRequest -> throw NotImplementedError()
            else -> throw NotImplementedError()
        }
    }

    override fun executeParallel(request: Request): Response {
        TODO("Not yet implemented")
    }

    override fun getFeatureById(id: String): ResultRow? {
        TODO("Not yet implemented")
    }

    override fun getFeaturesByIds(ids: List<String>): Map<String, String> {
        TODO("Not yet implemented")
    }
}
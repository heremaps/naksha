package com.here.naksha.lib.plv8.naksha.plv8

import naksha.model.IWriteSession
import naksha.model.NakshaContext
import naksha.model.NakshaFeatureProxy
import naksha.model.request.Request
import naksha.model.request.WriteRequest
import naksha.model.response.Response
import java.sql.Connection

class JvmPlv8WriteSession(
    connection: Connection,
    storage: JvmPlv8Storage,
    context: NakshaContext,
    stmtTimeout: Int,
    lockTimeout: Int
) : JvmPlv8ReadSession(connection, storage, context, stmtTimeout, lockTimeout), IWriteSession {

    override fun writeFeature(feature: NakshaFeatureProxy): Response {
        TODO("Not yet implemented")
    }

    override fun execute(request: Request): Response {
        return when (request) {
            is WriteRequest -> nakshaSession.write(writeRequest = request)
            else -> super.execute(request)
        }
    }

    override fun commit() {
        nakshaSession.sql.execute("commit")
    }

    override fun rollback() {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}
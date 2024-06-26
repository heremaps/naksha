package com.here.naksha.lib.plv8.naksha.plv8

import naksha.model.IWriteSession
import naksha.model.NakshaContext
import naksha.model.NakshaFeatureProxy
import naksha.model.request.Request
import naksha.model.request.WriteRequest
import naksha.model.response.Response

class PsqlWriteSession internal constructor(
    storage: PsqlStorage,
    options: PsqlConnectOptions,
    context: NakshaContext
) : PsqlReadSession(storage, context, options, true),
    IWriteSession {

    override var options: PsqlConnectOptions = options
        set(value) {
            require(!value.readOnly) {"Can't change write session to read-only connections"}
            field = value
        }

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
        nakshaSession.sql.execute("commit")
    }

    override fun close() {
        if (!_closed) {
            rollback()
            super.close()
        }
    }
}
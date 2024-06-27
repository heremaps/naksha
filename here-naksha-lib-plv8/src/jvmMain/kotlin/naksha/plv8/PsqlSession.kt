package com.here.naksha.lib.plv8.naksha.plv8

import naksha.model.ISession
import naksha.model.NakshaContext

/**
 * The PostgresQL session.
 */
abstract class PsqlSession internal constructor(): ISession {
    abstract override val context: NakshaContext
}

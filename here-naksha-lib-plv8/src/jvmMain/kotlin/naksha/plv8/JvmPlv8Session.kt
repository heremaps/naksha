package com.here.naksha.lib.plv8.naksha.plv8

import naksha.model.ISession
import naksha.model.NakshaContext

/**
 * Plv8 JVM abstract session it provides common behaviours for all sessions (read, write).
 */
abstract class JvmPlv8Session(
    override val context: NakshaContext,
    override val stmtTimeout: Int,
    override val lockTimeout: Int
) : ISession {
}
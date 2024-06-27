package naksha.plv8

import naksha.model.IStorage
import naksha.model.NakshaContext
import kotlin.js.JsExport

/**
 * The PostgresQL storage that manages session (aka connections). This is basically the [IStorage], but extended with some special methods
 * to acquire real PostgresQL database connections.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
interface PgStorage : IStorage {

    /**
     * Opens a new PostgresQL database session, which actually is backed by a dedicated conection. A session received through this method
     * will not really close, then [PgSession.close] is invoked, but return to the connection pool.
     * @param context the context for which to initialize the session.
     * @param options the options for the session.
     * @throws IllegalStateException If all connections are in use.
     */
    fun openSession(context: NakshaContext, options: PgSessionOptions): PgSession
}
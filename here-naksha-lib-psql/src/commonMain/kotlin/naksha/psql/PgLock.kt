@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.AtomicInt
import naksha.base.Int64
import naksha.base.NakshaException
import naksha.model.*
import naksha.base.NakshaError.NakshaError_C.ILLEGAL_STATE
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A simple wrapper for a PostgresQL session lock.
 * @since 3.0.0
 */
@OptIn(v30_experimental::class)
@JsExport
internal class PgLock internal constructor(
    /**
     * The session that created the lock.
     * @since 3.0.0
     */
    val session: PgSession,

    /**
     * The connection that holds the lock.
     * @since 3.0.0
     */
    val conn: PgConnection = session.pgConnection ?: throw NakshaException(ILLEGAL_STATE, "session.pgConnection must not be null"),

    /**
     * The lock-id used by the user.
     * @since 3.0.0
     */
    val lockId: String,

    /**
     * If this is a session or transaction lock.
     * @since 3.0.0
     */
    val isSessionLock: Boolean
) : ILock {

    companion object PgLock_C {
        /**
         * The [PlatformType] of [PgLock].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(PgLock::class).withPackageName(PACKAGE_NAME)
    }

    /**
     * The lock-number generated from the lock-id.
     * @since 3.0.0
     */
    val lockNumber: Int64 = PgUtil.lockId(lockId)

    init {
        if (isSessionLock) {
            conn.execute("SELECT pg_advisory_lock($1)", arrayOf(lockNumber)).close()
        } else {
            conn.execute("SELECT pg_advisory_xact_lock($1)", arrayOf(lockNumber)).close()
        }
    }

    override val storage: IStorage
        get() = session.storage

    private val closed = AtomicInt(1)

    override fun close() {
        if (closed.compareAndSet(1, 0) && !conn.isClosed()) {
            if (isSessionLock) {
                conn.execute("SELECT pg_advisory_unlock($1)", arrayOf(lockNumber)).close()
            }
            // Transaction locks are released automatically!
        }
    }

    override fun isClosed(): Boolean = closed.get() <= 0 || conn != session.pgConnection
}
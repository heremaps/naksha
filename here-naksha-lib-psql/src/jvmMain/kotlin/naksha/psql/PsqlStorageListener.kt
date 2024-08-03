package naksha.psql

import naksha.base.Platform.PlatformCompanion.logger
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import org.postgresql.PGNotification
import java.lang.ref.WeakReference
import java.sql.Statement
import java.util.concurrent.atomic.AtomicBoolean

internal class PsqlStorageListener(storage: PsqlStorage) : Thread("lib-psql-listener@${storage.id()}"), AutoCloseable {
    private val channel = storage.channel
    private val storageRef: WeakReference<PsqlStorage> = WeakReference(storage)
    private val shutdown = AtomicBoolean(false)
    private val adminOptions = storage.adminOptions
    private val cluster = storage.cluster
    private val e = Exception()

    init {
        isDaemon = true
        start()
    }

    override fun run() {
        var conn: PsqlConnection? = null
        var stmt: Statement?
        try {
            while (shutdown.get()) {
                try {
                    if (conn == null) {
                        conn = cluster.newConnection(adminOptions, false)
                        stmt = conn.jdbc.createStatement()
                        stmt.execute("LISTEN ${quoteIdent(channel)}")
                    }
                    var notifications: Array<out PGNotification>?
                    synchronized(this) {
                        notifications = conn!!.jdbc.getNotifications(1000)
                    }
                    var storage = storageRef.get()
                    if (storage == null) {
                        if (!shutdown.get()) logger.error("Storage was garbage collected, but not closed before: {}", e)
                        shutdown.set(true)
                        return
                    }
                    val n = notifications
                    if (n != null) processNotifications(storage, n)
                    // We want to be sure, that no compiler optimization prevents a garbage collection of the storage!
                    @Suppress("UNUSED_VALUE")
                    storage = null
                } catch (_: InterruptedException) {
                } catch (e: Throwable) {
                    logger.error("Unexpected exception, closing connection, and re-establish it: {}", e)
                    val c = conn
                    conn = null
                    @Suppress("UNUSED_VALUE")
                    stmt = null
                    try {
                        c?.close()
                    } catch (_: Throwable) {
                    }
                }
            }
        } finally {
            val c = conn
            conn = null
            @Suppress("UNUSED_VALUE")
            stmt = null
            try {
                c?.close()
            } catch (_: Throwable) {
            }
        }
    }

    private fun processNotifications(storage: PsqlStorage, notifications: Array<out PGNotification>) {
        // TODO: Process the notifications for NakshaCache evictions !!!
        for (notification in notifications) {
            logger.info("Received notification on channel '${notification.name}': '${notification.parameter}'")
        }
    }

    override fun close() {
        shutdown.set(true)
        synchronized(this) {
            interrupt()
        }
    }
}
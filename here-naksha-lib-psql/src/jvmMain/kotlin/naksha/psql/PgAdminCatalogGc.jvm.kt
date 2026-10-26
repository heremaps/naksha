package naksha.psql

import naksha.base.Id
import naksha.base.Platform
import naksha.base.WeakRef
import naksha.base.illegalState
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit.MILLISECONDS

actual class PgAdminCatalogManager private actual constructor(adminCatalog: PgAdminCatalog): Runnable {
    private val id = Id(adminCatalog.id)
    private val weakRef = WeakRef(adminCatalog)
    private val shutdownHook = thread(start = false) { onShutdown() }
    private val backgroundJob = Thread(managerThreadGroup, this, id.text)
    private val closed = AtomicBoolean(false)
    init {
        Runtime.getRuntime().addShutdownHook(shutdownHook)
        backgroundJob.start()
    }

    // ————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————
    // The actual code that does the management job.
    // ————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————

    /**
     * Do some initialization works.
     * @since 3.0
     */
    private fun init(adminCatalog: PgAdminCatalog) {

    }

    /**
     * The actual work to be done.
     * @param adminCatalog the dereferenced admin catalog to work with.
     * @since 3.0
     */
    private fun doWork(adminCatalog: PgAdminCatalog) {

    }

    /**
     * Invoked exactly ones when the manager goes down.
     *
     * This happens for two reasons, either because the system is shutting down or the catalog has been garbage collected.
     *
     * @param adminCatalog the dereferenced admin catalog to work with, if still being available; generally should be expected to be `null`, except the system is shutting down.
     * @since 3.0
     */
    private fun finish(adminCatalog: PgAdminCatalog?) {

    }

    // ————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————
    // The code that calls the work code.
    // ————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————

    /**
     * Sleep for the given amount of time.
     * @param duration the sleep duration.
     * @return _true_ if the sleep was interrupted; _false_ otherwise.
     */
    private fun sleep(duration: Duration): Boolean {
        val sleepMillis = duration.toLong(MILLISECONDS)
        if (sleepMillis > 0) {
            try {
                Thread.sleep(sleepMillis)
            } catch (_: InterruptedException) {
                return true
            }
        }
        return false
    }

    private fun onShutdown() { close() }

    override fun run() {
        if (Thread.currentThread() != backgroundJob) {
            throw illegalState("The admin manager run method must not be called from external!")
        }
        var adminCatalog: PgAdminCatalog? = weakRef.deref()
        if (adminCatalog != null) {
            try {
                init(adminCatalog)
            } catch (e: Exception) {
                Platform.logger.error("Failed to initialize manager for admin catalog '$id': ${e.message}", e)
                close()
                return
            }
        }
        while (adminCatalog != null) {
            try {
                val start = System.nanoTime()
                doWork(adminCatalog)
                val end = System.nanoTime()
                val runDuration = (end - start).nanoseconds
                val sleepDuration = RUNTIME - runDuration
                sleep(sleepDuration)
            } catch (e: Exception) {
                Platform.logger.error("Unexpected exception in manager for admin catalog '$id': ${e.message}", e)
                sleep(100.milliseconds)
            }
            adminCatalog = weakRef.deref()
        }
        close()
    }

    private fun close() {
        managerInstances.remove(id, this)
        if (!shutdownHook.isAlive) {
            Runtime.getRuntime().removeShutdownHook(shutdownHook)
        }
        if (closed.compareAndSet(false, true)) {
            try {
                finish(weakRef.deref())
            } catch (e: Exception) {
                Platform.logger.error("Unexpected exception in finishing manager for admin catalog '$id': ${e.message}", e)
            }
        }
    }

    actual companion object PgAdminCatalogManagerCompanion {
        /**
         * How often the manager should [doWork]. A duration to avoid running it too often.
         * @since 3.0
         */
        private val RUNTIME = 1.minutes
        private val managerThreadGroup = ThreadGroup("naksha.Manager")
        private val managerInstances = ConcurrentHashMap<Id, Any>()

        actual fun get(adminCatalog: PgAdminCatalog): PgAdminCatalogManager? {
            val id = Id(adminCatalog.id)
            return managerInstances[id] as? PgAdminCatalogManager
        }

        actual fun of(adminCatalog: PgAdminCatalog): PgAdminCatalogManager {
            val id = Id(adminCatalog.id)
            while (true) {
                var manager = managerInstances[id]
                if (manager is PgAdminCatalogManager) return manager
                if (manager is Boolean) {
                    Thread.yield()
                    continue
                }
                // Add allocations mark
                // When we were able to insert the mark, we are sure we are the only one creating the manager.
                val MARK = Any()
                if (managerInstances.putIfAbsent(id, MARK) == null) {
                    try {
                        manager = PgAdminCatalogManager(adminCatalog)
                        managerInstances[id] = manager
                        return manager
                    } catch (e: Exception) {
                        Platform.logger.error("Unexpected exception while creating manager for admin catalog '$id': ${e.message}", e)
                        // Remove the MARK, we were unable to create the manager.
                        managerInstances.remove(id, MARK)
                        throw e
                    }
                }
                // Someone else will allocate the manager.
                Thread.yield()
            }
        }
    }
}
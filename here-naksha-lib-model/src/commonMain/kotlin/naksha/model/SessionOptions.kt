@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.fn.Fn3
import naksha.model.objects.NakshaFeature
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * Optional options when acquiring a new session.
 *
 * @since 3.0.0
 */
@JsExport
data class SessionOptions(
    /**
     * The map-id of the map to operate on, by default taken from [context][NakshaContext.mapId].
     * @since 3.0.0
     */
    @JvmField
    val mapId: String = NakshaContext.mapId(),

    /**
     * An arbitrary name for debug logs, in `lib-psql` this will be used in the database connection as name and shown in `pg_stat_activity`.
     * @since 3.0.0
     */
    @JvmField
    val appName: String = NakshaContext.appName(),

    /**
     * The application that acts.
     * @since 3.0.0
     */
    @JvmField
    val appId: String = NakshaContext.appId(),

    /**
     * The author that acts; if any.
     * @since 3.0.0
     */
    @JvmField
    val author: String? = NakshaContext.author(),

    /**
     * Allow optimiser to execute requests in parallel, as long as it can provide similar guarantees that a single, not parallel session, would grant.
     *
     * Often this is not possible for writing, but for reading, where failures can be bypassed by simply repeating the operation or falling back to a single connection. Note that parallel writing may use much more connection in parallel, this can be a problem in some situations, in these the feature can be disabled. Beware that this option is ignored when a parallel execution is forced via [ISession.executeParallel].
     * @since 3.0.0
     */
    @JvmField
    val parallel: Boolean = true,

    /**
     * Only use the master node to avoid replication lag.
     * @since 3.0.0
     */
    @JvmField
    val useMaster: Boolean = false,

    /**
     * When calculating the hash of a feature, the paths that should be excluded from hash calculation.
     * @since 3.0.0
     */
    @JvmField
    val excludePaths: List<Array<String>>? = NakshaContext.currentContext().excludePaths,

    /**
     * When calculating the hash of a feature, a function to be called for every property to hash.
     *
     * The function receives the feature that is being hashed, the current path, and the value to be hashed (will be _null_, _String_, _Int_, _Int64_, _Double_ or _Boolean_). It should return _true_, when the value should be part of the hash; _false_ otherwise.
     * @since 3.0.0
     */
    @JvmField
    val excludeFn: Fn3<Boolean, NakshaFeature, List<String>, Any?>? = NakshaContext.currentContext().excludeFn,

    /**
     * The time in milliseconds to wait for the TCP handshake.
     * @since 3.0.0
     */
    @JvmField
    val connectTimeout: Int = NakshaContext.currentContext().connectTimeout,

    /**
     * The time in milliseconds to wait for the TCP socket when reading or writing from it.
     * @since 3.0.0
     */
    @JvmField
    val socketTimeout: Int = NakshaContext.currentContext().socketTimeout,

    /**
     * The statement-timeout in milliseconds, this means how long to wait for each CREATE, UPDATE or DELETE to be executed.
     * @since 3.0.0
     */
    @JvmField
    val stmtTimeout: Int = NakshaContext.currentContext().stmtTimeout,

    /**
     * The lock-timeout in milliseconds, when the storage has to use locking.
     * @since 3.0.0
     */
    @JvmField
    val lockTimeout: Int = NakshaContext.currentContext().lockTimeout
) {

    /**
     * Returns the actor, which is either the [author], or if no [author] is available, the [appId].
     * @since 3.0.0
     */
    val actor: String
        get() = author ?: appId

    companion object SessionOptions_C {
        /**
         * Helper for JavaScript and Java to create a new default instance without providing too many arguments.
         * @param context the context, if being _null_, then [NakshaContext.currentContext] is called.
         * @return the session options.
         */
        @JvmStatic
        @JsStatic
        fun from(context: NakshaContext?): SessionOptions {
            val c = context ?: NakshaContext.currentContext()
            return SessionOptions(
                mapId = c.mapId,
                appName = c.appName,
                appId = c.appId,
                author = c.author,
                excludePaths = c.excludePaths,
                excludeFn = c.excludeFn,
                connectTimeout = c.connectTimeout,
                socketTimeout = c.socketTimeout,
                stmtTimeout = c.stmtTimeout,
                lockTimeout = c.lockTimeout
            )
        }
    }
}
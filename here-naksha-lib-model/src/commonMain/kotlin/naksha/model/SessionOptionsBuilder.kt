@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Id
import naksha.base.Int64
import naksha.base.fn.Fn3
import naksha.base.illegalState
import naksha.model.objects.NakshaFeature
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Builder for [SessionOptions].
 *
 * Should be constructed with a [IStorage] argument or the `id` of the database, because this is mandatory. Every value not being set, read defaults from the [NakshaContext] passed to [build] or requested from [NakshaContext.currentContext].
 *
 * @since 3.0
 */
@JsExport
class SessionOptionsBuilder() {

    @JsName("forStorage")
    constructor(storage: IStorage) : this() {
        databaseId = storage.defaultDatabaseId
    }

    @JsName("forDatabaseId")
    constructor(databaseId: Id) : this() {
        this.databaseId = databaseId
    }

    private var databaseId: Id? = null
    private var appName: String? = null
    private var appId: String? = null
    private var author: String? = null
    private var parallel: Boolean? = null
    private var useMaster: Boolean? = null
    private var excludePaths: List<Array<String>>? = null
    private var excludeFn: Fn3<Boolean, NakshaFeature, List<String>, Any?>? = null
    private var connectTimeout: Int? = null
    private var socketTimeout: Int? = null
    private var stmtTimeout: Int? = null
    private var lockTimeout: Int? = null
    private var idleTxTimeout: Int? = null
    private var streamInfo: StreamInfo? = null
    private var authToken: String? = null
    private var logLevel: String? = null

    /**
     * @since 3.0
     */
    fun withDatabaseId(databaseId: Id): SessionOptionsBuilder {
        this.databaseId = databaseId
        return this
    }

    /**
     * @since 3.0
     */
    fun withAppName(appName: String): SessionOptionsBuilder {
        this.appName = appName
        return this
    }

    /**
     * @since 3.0
     */
    fun withAppId(appId: String): SessionOptionsBuilder {
        this.appId = appId
        return this
    }

    /**
     * @since 3.0
     */
    fun withAuthor(author: String?): SessionOptionsBuilder {
        this.author = author
        return this
    }

    /**
     * @since 3.0
     */
    fun withParallel(parallel: Boolean): SessionOptionsBuilder {
        this.parallel = parallel
        return this
    }

    /**
     * @since 3.0
     */
    fun withUseMaster(useMaster: Boolean): SessionOptionsBuilder {
        this.useMaster = useMaster
        return this
    }

    /**
     * @since 3.0
     */
    fun withExcludePaths(excludePaths: List<Array<String>>?): SessionOptionsBuilder {
        this.excludePaths = excludePaths
        return this
    }

    /**
     * @since 3.0
     */
    fun withExcludeFn(excludeFn: Fn3<Boolean, NakshaFeature, List<String>, Any?>?): SessionOptionsBuilder {
        this.excludeFn = excludeFn
        return this
    }

    /**
     * @since 3.0
     */
    fun withConnectTimeout(connectTimeout: Int): SessionOptionsBuilder {
        this.connectTimeout = connectTimeout
        return this
    }

    /**
     * @since 3.0
     */
    fun withSocketTimeout(socketTimeout: Int): SessionOptionsBuilder {
        this.socketTimeout = socketTimeout
        return this
    }

    /**
     * @since 3.0
     */
    fun withStmtTimeout(stmtTimeout: Int): SessionOptionsBuilder {
        this.stmtTimeout = stmtTimeout
        return this
    }

    /**
     * @since 3.0
     */
    fun withLockTimeout(lockTimeout: Int): SessionOptionsBuilder {
        this.lockTimeout = lockTimeout
        return this
    }

    /**
     * @since 3.0
     */
    fun withIdleTxTimeout(idleTxTimeout: Int): SessionOptionsBuilder {
        this.idleTxTimeout = idleTxTimeout
        return this
    }

    /**
     * @since 3.0
     */
    fun withStreamInfo(streamInfo: StreamInfo): SessionOptionsBuilder {
        this.streamInfo = streamInfo
        return this
    }

    /**
     * @since 3.0
     */
    fun withAuthToken(authToken: String?): SessionOptionsBuilder {
        this.authToken = authToken
        return this
    }

    /**
     * @since 3.0
     */
    fun withLogLevel(logLevel: String?): SessionOptionsBuilder {
        this.logLevel = logLevel
        return this
    }

    /**
     * Builds the [SessionOptions], resolving any unset values from the current [NakshaContext].
     *
     * @since 3.0
     */
    fun build(): SessionOptions = build(NakshaContext.currentContext())

    /**
     * Builds the [SessionOptions], resolving any unset values from the given [NakshaContext].
     *
     * @param context the [NakshaContext] to use to fill unset values.
     * @since 3.0
     */
    @JsName("buildWithContext")
    fun build(context: NakshaContext): SessionOptions = SessionOptions(
        databaseId = databaseId ?: throw illegalState("Build failed for missing databaseId"),
        appName = appName ?: context.appName,
        appId = appId ?: context.appId,
        author = author ?: context.author,
        parallel = parallel ?: true,
        useMaster = useMaster ?: false,
        excludePaths = excludePaths ?: context.excludePaths,
        excludeFn = excludeFn ?: context.excludeFn,
        connectTimeout = connectTimeout ?: context.connectTimeout,
        socketTimeout = socketTimeout ?: context.socketTimeout,
        stmtTimeout = stmtTimeout ?: context.stmtTimeout,
        lockTimeout = lockTimeout ?: context.lockTimeout,
        idleTxTimeout = idleTxTimeout ?: context.idleTxTimeout,
        streamInfo = streamInfo ?: context.streamInfo,
        authToken = authToken,
        logLevel = logLevel ?: Naksha.DEFAULT_SESSION_LOG_LEVEL
    )
}

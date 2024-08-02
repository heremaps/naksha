@file:Suppress("MemberVisibilityCanBePrivate", "OPT_IN_USAGE", "NON_EXPORTABLE_TYPE", "UNCHECKED_CAST")

package naksha.model

import naksha.auth.UserRightsMatrix
import naksha.base.*
import naksha.base.fn.Fn0
import naksha.base.fn.Fn3
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.objects.NakshaFeature
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass

/**
 * The Naksha Context, a thread-local, that stores credentials, and other thread local information. The main purpose is to ensure that all
 * entities can perform authorization. It is normally created, when a new request is started, using the static [newInstance] factory method
 * and then attached to the current thread.
 * @since 2.0.5
 * @see newInstance
 */
@JsExport
open class NakshaContext protected constructor() {
    /**
     * The time in milliseconds to wait for the TCP handshake.
     */
    open var connectTimeout: Int = defaultConnectTimeout.get()

    /**
     * The time in milliseconds to wait for the TCP socket when reading or writing from it.
     */
    open val socketTimeout: Int = defaultSocketTimeout.get()

    /**
     * The statement-timeout in milliseconds, this means how long to wait for each CREATE, UPDATE or DELETE to be executed.
     */
    open val stmtTimeout: Int = defaultStmtTimeout.get()

    /**
     * The lock-timeout in milliseconds, when the storage has to use locking.
     */
    open val lockTimeout: Int = defaultLockTimeout.get()

    private var _appName: String? = null

    /**
     * The application name, like the user-agent.
     */
    open var appName: String
        get() = _appName ?: defaultAppName.get() ?: DEFAULT_APP_NAME
        set(value) {
            _appName = value
        }

    private var _appId: String? = null

    /**
     * The application identifier of the client that acts. It is used at many places, for authorization, ownership of features and logging.
     */
    open var appId: String
        get() = _appId ?: defaultAppId.get() ?: DEFAULT_APP_ID
        set(value) {
            _appId = value
        }

    /**
     * Returns the appId or the given alternative.
     * @param alternative the alternative to return, when no appId is available.
     * @return the appId.
     */
    open fun getAppIdOr(alternative: String): String = _appId ?: alternative

    /**
     * Returns the appId or throws a [NakshaError.ILLEGAL_STATE].
     * @return the appId.
     */
    open fun getAppIdOrThrow(msgFn: Fn0<String>? = null): String =
        _appId ?: throw NakshaException(ILLEGAL_STATE, msgFn?.call() ?: "The current context has no appId")

    /**
     * Changes the application-identifier and returns the [NakshaContext].
     * @param appId the new app-id.
     * @return this.
     */
    open fun withAppId(appId: String): NakshaContext {
        this._appId = appId
        return this
    }

    /**
     * The internal field of the **streamId** setter and getter.
     */
    private var _streamId: String? = null

    /**
     * The stream-identifier being used in logging to group log entries that belong to the same request.
     */
    open var streamId: String
        get() {
            var s = _streamId
            if (s == null) {
                s = PlatformUtil.randomString()
                _streamId = s
            }
            return s
        }
        set(value) {
            _streamId = value
        }

    /**
     * Changes the stream-id and returns the [NakshaContext].
     * @param streamId the new stream-id.
     * @return this.
     */
    open fun withStreamId(streamId: String): NakshaContext {
        this.streamId = streamId
        return this
    }

    /**
     * The internal field of the **author** getter and setter.
     */
    private var _author: String? = null

    /**
     * The author. The author represents the human user that acts, if any. It is used at many places, for authorization, ownership of
     * features and logging.
     * @since 2.0.7
     */
    open var author: String?
        get() = _author
        set(value) {
            _author = value
        }

    /**
     * Changes the author and returns the [NakshaContext].
     * @param author the new author.
     * @return this.
     */
    open fun withAuthor(author: String?): NakshaContext {
        this.author = author
        return this
    }

    /**
     * The map to use.
     *
     * The map is read from the JWT `map` claim, but can be overridden by the client using the HTTP header `X-Map` or by using specially crafted requests which explicitly specify the map. If neither is available, the default is [DEFAULT_MAP].
     *
     * Note: In `lib-psql` the default map is mapped to the default schema configured within the storage driver. Other maps are simply stored in their own dedicated schemas with the same name (this only applies for the default PostgresQL storage implementation).
     * @since 3.0.0
     */
    open var map: String = DEFAULT_MAP

    /**
     * Change the current map.
     * @param map the map to select.
     * @return this.
     */
    open fun withMap(map: String): NakshaContext {
        this.map = map
        return this
    }

    /**
     * If the super-user flag is enabled. This normally is only done temporarily.
     * @since 2.0.7
     */
    open var su: Boolean = false

    /**
     * The User-Rights-Matrix for authentication.
     * @since 2.0.16
     */
    open var urm: UserRightsMatrix? = null

    /**
     * Changes the URM and returns the [NakshaContext].
     * @param urm the new User-Rights-Matrix.
     * @return this.
     * @since 2.0.16
     */
    open fun withUrm(urm: UserRightsMatrix?): NakshaContext {
        this.urm = urm
        return this
    }

    /**
     * Returns the _actor_, which is normally the [author]. If author is _null_, then it returns the [appId].
     * @return the actor.
     * @since 2.0.15
     */
    open fun getActor(): String = author ?: appId

    /**
     * Returns the _actor_, which is normally the [author]. If author is _null_, then it returns the [appId].
     * @param alternative the alternative to return, when no [author] and no [appId] are available.
     * @param errMsgFn a function to generate an error message, when no [author], no [appId], and no [alternative] are available.
     * @return the actor.
     * @since 2.0.15
     */
    @JsName("getActorOrThrow")
    open fun getActor(alternative: String? = null, errMsgFn: Fn0<String>? = null): String {
        return author ?: _appId ?: alternative ?: throw NakshaException(ILLEGAL_STATE, errMsgFn?.call()?: "Missing actor")
    }

    /**
     * When calculating the hash of a feature, the paths that should be excluded from hash calculation.
     */
    open var excludePaths: List<Array<String>>? = null
        get() = if (field == null) defaultExcludePaths.get() else field

    /**
     * When calculating the hash of a feature, a function to be called for every property to hash.
     *
     * The function receives the feature that is being hashed, the current path, and the value to be hashed (will be _null_, _String_, _Int_, _Int64_, _Double_ or _Boolean_). It should return _true_, when the value should be part of the hash; _false_ otherwise.
     */
    open var excludeFn: Fn3<Boolean, NakshaFeature, List<String>, Any?>? = null
        get() = if (field == null) defaultExcludeFn.get() else field

    /**
     * Arbitrary attachments.
     */
    @JvmField
    val attachments: AtomicMap<Any, Any> = Platform.newAtomicMap()

    /**
     * Returns the attachment of the given type.
     * @param attachmentType the type ([KClass]) of the attachment to get.
     * @return the attachment or _null_, if no such attachment is available.
     */
    open operator fun <T : Any> get(attachmentType: KClass<T>): T? {
        val value = attachments[attachmentType]
        return if (attachmentType.isInstance(value)) value as T else null
    }

    /**
     * Tests if an attachment with the given key exists, normally the type ([KClass]) of the attachment is used.
     * @param attachmentType the key to test.
     * @return _true_ if such a key exists; _false_ otherwise.
     */
    open operator fun contains(attachmentType: KClass<*>): Boolean = attachments.containsKey(attachmentType)

    /**
     * Sets the key to the given value.
     * @param key the key to set.
     * @param value the value to set.
     */
    open operator fun <T : Any> set(key: KClass<T>, value: T) {
        attachments[key] = value
    }

    /**
     * Adds the given attachment, if there is an attachment of the same type already, overrides it. The key will be the type of the attachment.
     * @param attachment the attachment to add.
     * @return this.
     */
    open fun add(attachment: Any): NakshaContext {
        val key = attachment::class
        attachments[key] = attachment
        return this
    }

    /**
     * Adds the given attachment, if there is an attachment of the same type already, overrides it. The key will be the type of the attachment.
     * @param attachment the attachment to add.
     * @return the previously set value.
     */
    open fun put(attachment: Any): Any? {
        return attachments.put(attachment::class, attachment)
    }

    /**
     * Adds the give attachment, if no such attachment is already contained.
     * @param attachment the attachment to add.
     * @return _true_ if added; _false_ otherwise.
     */
    open fun putIfAbsent(attachment: Any): Boolean {
        return attachments.putIfAbsent(attachment::class, attachment) == null
    }

    /**
     * Tries to replace an existing value with a new one, using an atomic operation.
     * @param existing the existing value.
     * @param value the new value.
     * @return _true_ if the replacement was successful; _false_ otherwise.
     */
    open fun <T : Any> replace(existing: T, value: T): Boolean {
        return attachments.replace(existing::class, existing, value)
    }

    /**
     * Removes the attachment assigned to the given key.
     * @param attachmentType the key to remove.
     * @return the currently assigned value.
     */
    open fun <T : Any> remove(attachmentType: KClass<T>): T? {
        val raw = attachments.remove(attachmentType)
        return if (attachmentType.isInstance(raw)) raw as T else null
    }

    /**
     * The epoch micro-second when the context was created. Is used in logging to log relative timestamps and can be used elsewhere for
     * relative timestamps (time since start of a request).
     */
    @JvmField
    val startMicros: Int64 = Platform.currentMicros()

    /**
     * Attaches this context to the current thread.
     * @return this.
     */
    open fun attachToCurrentThread(): NakshaContext {
        threadLocal.set(this)
        return this
    }

    /**
     * Stream information.
     */
    open var streamInfo: StreamInfo? = null

    @Suppress("OPT_IN_USAGE")
    companion object NakshaContextCompanion {
        /**
         * The immutable default app-name to be used, if nothing else is available (defined at build time).
         */
        const val DEFAULT_APP_NAME = "NakshaClient/${NakshaVersion.LATEST}"

        /**
         * The immutable default app-id to be used, if nothing else is available (defined at build time).
         */
        const val DEFAULT_APP_ID = "anonymous"

        /**
         * The default application name to use.
         */
        @JvmField
        val defaultAppName = AtomicRef(DEFAULT_APP_NAME)

        /**
         * The default application identifier to use.
         */
        @JvmField
        val defaultAppId = AtomicRef(DEFAULT_APP_ID)

        /**
         * The default exclude path to use, when calculating hashes.
         *
         * This is an application wide setting, that when not being _null_, will cause all contexts that have no exclude path, to use this one!
         */
        @JvmField
        val defaultExcludePaths = AtomicRef<List<Array<String>>>(null)

        /**
         * The default exclude function to use, when calculating hashes.
         *
         * This is an application wide setting, that when not being _null_, will cause all contexts that have no exclude function, to use this one!
         */
        @JvmField
        val defaultExcludeFn = AtomicRef<Fn3<Boolean, NakshaFeature, List<String>, Any?>>(null)

        /**
         * The application wide default time in milliseconds to wait for the TCP handshake.
         */
        @JvmField
        val defaultConnectTimeout = AtomicInt(60_000)

        /**
         * The application wide default time in milliseconds to wait for the TCP socket when reading or writing from it.
         */
        @JvmField
        val defaultSocketTimeout = AtomicInt(60_000)

        /**
         * The application wide default statement-timeout in milliseconds, this means how long to wait for each CREATE, UPDATE or DELETE to be executed.
         */
        @JvmField
        val defaultStmtTimeout = AtomicInt(60_000)

        /**
         * The application wide default lock-timeout in milliseconds, when the storage has to use locking.
         */
        @JvmField
        val defaultLockTimeout = AtomicInt(10_000)

        /**
         * The default map, being an empty string.
         */
        const val DEFAULT_MAP = ""

        /**
         * Returns the current map.
         * @return the current map.
         */
        @JvmStatic
        @JsStatic
        fun map(): String = currentContext().map

        /**
         * Returns the current application name.
         * @return the current application name.
         */
        @JvmStatic
        @JsStatic
        fun appName(): String = currentContext().appName

        /**
         * Returns the current application identifier.
         * @return the current application identifier.
         */
        @JvmStatic
        @JsStatic
        fun appId(): String = currentContext().appId

        /**
         * Returns the current author.
         * @return the current author.
         */
        @JvmStatic
        @JsStatic
        fun author(): String? = currentContext().author

        /**
         * The thread local that stores the [NakshaContext].
         */
        @JvmStatic
        protected var threadLocal: PlatformThreadLocal<NakshaContext> = Platform.newThreadLocal(::NakshaContext)

        /**
         * Can be overridden by application code to modify the context creation.
         */
        @JvmStatic
        @JsStatic
        var constructorRef: Fn0<NakshaContext> = Fn0(::NakshaContext)

        /**
         * Can be overridden by application code to modify the thread local context gathering.
         */
        @JvmStatic
        @JsStatic
        var currentRef: Fn0<NakshaContext> = Fn0(threadLocal::get)

        /**
         * Creates a new Naksha Context.
         * @param appId The application-id for which to create the context.
         * @param author The author.
         * @param su If the user is a permanent super-user.
         */
        // TODO: Kotlin-Compiler-Bug: We need open, otherwise Java can't create another static method with the same name in extending class!
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic
        @JsStatic
        open fun newInstance(appId: String, author: String? = null, streamId: String? = null, su: Boolean = false): NakshaContext {
            val context = constructorRef.call()
            context.appId = appId
            context.author = author
            if (streamId != null) {
                context.streamId = streamId
            }
            context.su = su
            return context
        }

        /**
         * Returns the current context. If no context exists, creates a new context and binds it to the thread-local.
         * Note that when a new context is created, any reading of the [appId] will raise a [IllegalStateException].
         * @return The current context.
         */
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic
        @JsStatic
        open fun currentContext(): NakshaContext = threadLocal.get()
    }
}

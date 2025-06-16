@file:Suppress("MemberVisibilityCanBePrivate", "OPT_IN_USAGE", "NON_EXPORTABLE_TYPE", "UNCHECKED_CAST")

package naksha.model

import naksha.auth.UserRightsMatrix
import naksha.base.*
import naksha.base.fn.Fn0
import naksha.base.fn.Fn3
import naksha.base.NakshaError.NakshaError_C.ILLEGAL_STATE
import naksha.base.Platform.Platform_C.forKClass
import naksha.model.objects.NakshaFeature
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass

// TODO: As multiple threads can share the same context, we need to make it thread safe, so use AtomicMap in the background!
//       Basically, this needs to be done the same way that StreamInfo was made thread safe!
//       We can derive both from the same base class, something like ThreadSafeObject or whatever.

/**
 * The Naksha Context is a thread-local that stores credentials, and request information.
 *
 * The main purpose is to ensure that all entities can perform authorization, and share debugging information, like the stream-identifier for logging. It is recommended that each application creates its own stream-information class, with own special properties next to the shared general ones.
 *
 * It is normally created, when a new request is started, using the static [newInstance] factory method, and then attached to the current thread:
 *
 * ```kotlin
 * ```
 * @since 2.0.5
 * @see newInstance
 * @see attachToCurrentThread
 */
@JsExport
open class NakshaContext protected constructor() {
    /**
     * The time in milliseconds to wait for the TCP handshake.
     * @since 3.0
     */
    open var connectTimeout: Int = defaultConnectTimeout.get()

    /**
     * The time in milliseconds to wait for the TCP socket when reading or writing from it.
     * @since 3.0
     */
    open val socketTimeout: Int = defaultSocketTimeout.get()

    /**
     * The statement-timeout in milliseconds, this means how long to wait for each CREATE, UPDATE or DELETE to be executed.
     * @since 3.0
     */
    open val stmtTimeout: Int = defaultStmtTimeout.get()

    /**
     * The lock-timeout in milliseconds, when the storage has to use locking.
     * @since 3.0
     */
    open val lockTimeout: Int = defaultLockTimeout.get()

    /**
     * The idle-transaction-timeout in milliseconds.
     * @since 3.0
     */
    open val idleTxTimeout = defaultIdleTxTimeout.get()

    /**
     * Whenever the pipeline of a space is entered, the `id` of the space is pushed to the end of the `spaceIds` list, and when the pipeline is left, the last `id` is removed from the `spaceIds` list.
     * @since 3.0
     */
    open val spaceIds: StringList = StringList()

    private var _appName: String? = null

    /**
     * The application name, like the user-agent.
     * @since 2.0.7
     */
    open var appName: String
        get() = _appName ?: defaultAppName.get() ?: throw NakshaException(ILLEGAL_STATE, "Missing appName")
        set(value) {
            _appName = value
        }

    private var _appId: String? = null

    /**
     * The application identifier of the client that acts. It is used at many places, for authorization, ownership of features and logging.
     * @since 2.0.7
     */
    open var appId: String
        get() = _appId ?: defaultAppId.get() ?: throw NakshaException(ILLEGAL_STATE, "Missing appId")
        set(value) {
            _appId = value
        }

    /**
     * Returns the appId or the given alternative.
     * @param alternative the alternative to return, when no appId is available.
     * @return the appId.
     * @since 2.0.7
     */
    open fun getAppIdOr(alternative: String): String = _appId ?: alternative

    /**
     * Returns the appId or throws a [ILLEGAL_STATE].
     * @return the appId.
     * @since 2.0.7
     */
    open fun getAppIdOrThrow(msgFn: Fn0<String>? = null): String =
        _appId ?: throw NakshaException(ILLEGAL_STATE, msgFn?.call() ?: "The current context has no appId")

    /**
     * Changes the application-identifier and returns the [NakshaContext].
     * @param appId the new app-id.
     * @return this.
     * @since 2.0.7
     */
    open fun withAppId(appId: String): NakshaContext {
        this._appId = appId
        return this
    }

    private var _streamInfo: StreamInfo? = null

    /**
     * The stream-information.
     * @since 3.0
     */
    open var streamInfo: StreamInfo
        get() {
            var s = _streamInfo
            if (s == null) {
                s = streamInfoConstructorRef.call()
                _streamInfo = s
            }
            return s
        }
        set(value) {
            _streamInfo = value
        }

    /**
     * @see [streamInfo]
     */
    open fun withStreamInfo(value: StreamInfo): NakshaContext {
        streamInfo = value
        return this
    }

    /**
     * The stream-identifier being used in logging to group log entries that belong to the same request.
     *
     * ### Warning
     * Changing the stream-identifier causes a new [StreamInfo] to be created, so [streamInfo] will change too!
     * @since 2.0.7
     */
    open var streamId: String
        get() {
            var s = _streamInfo
            if (s == null) {
                s = streamInfoConstructorRef.call()
                _streamInfo = s
            }
            return s.streamId
        }
        set(value) {
            val currentInfo = _streamInfo
            if (currentInfo != null && currentInfo.streamId == value) return
            // Create a new stream-information with the desired stream-id.
            val newInfo = streamInfoConstructorRef.call()
            newInfo.streamId = value
            _streamInfo = newInfo
        }

    /**
     * Changes the stream-id and returns the [NakshaContext].
     *
     * ### Warning
     * Changing the stream-identifier causes a new [StreamInfo] to be created, so [streamInfo] will change too!
     * @param streamId the new stream-id.
     * @return this.
     * @since 2.0.7
     */
    open fun withStreamId(streamId: String): NakshaContext {
        this.streamId = streamId
        return this
    }

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
     * @since 2.0.7
     */
    open fun withAuthor(author: String?): NakshaContext {
        this.author = author
        return this
    }

    /**
     * If the super-user flag is enabled. This normally is only done temporarily.
     * @since 2.0.7
     */
    open var su: Boolean = false

    /**
     * Set the super-user flag.
     * @param su enable or disable super-user flag.
     * @return this
     * @since 3.0
     */
    open fun withSu(su: Boolean): NakshaContext {
        this.su = su
        return this
    }

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

    @Suppress("OPT_IN_USAGE")
    companion object NakshaContext_C {
        /**
         * The [PlatformType] of [NakshaContext].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(NakshaContext::class).withPackageName(PACKAGE_NAME)

        /**
         * The default application name to use, defaults to `NakshaClient/{version}`.
         * @since 3.0
         */
        @JvmField
        val defaultAppName = AtomicRef("NakshaClient/${NakshaVersion.CURRENT}")

        /**
         * The default application identifier to use, defaults to `null`.
         * @since 3.0
         */
        @JvmField
        val defaultAppId = AtomicRef<String>(null)

        /**
         * The default exclude path to use, when calculating hashes.
         *
         * This is an application wide setting, that when not being _null_, will cause all contexts that have no exclude path, to use this one!
         * @since 3.0
         */
        @JvmField
        val defaultExcludePaths = AtomicRef<List<Array<String>>>(null)

        /**
         * The default exclude function to use, when calculating hashes.
         *
         * This is an application wide setting, that when not being _null_, will cause all contexts that have no exclude function, to use this one!
         * @since 3.0
         */
        @JvmField
        val defaultExcludeFn = AtomicRef<Fn3<Boolean, NakshaFeature, List<String>, Any?>>(null)

        /**
         * The application wide default time in milliseconds to wait for the TCP handshake.
         * @since 3.0
         */
        @JvmField
        val defaultConnectTimeout = AtomicInt(60_000)

        /**
         * The application wide default time in milliseconds to wait for the TCP socket when reading or writing from it.
         * @since 3.0
         */
        @JvmField
        val defaultSocketTimeout = AtomicInt(60_000)

        /**
         * The application wide default statement-timeout in milliseconds, this means how long to wait for each CREATE, UPDATE or DELETE to be executed.
         * @since 3.0
         */
        @JvmField
        val defaultStmtTimeout = AtomicInt(60_000)

        /**
         * The application wide default lock-timeout in milliseconds, when the storage has to use locking.
         * @since 3.0
         */
        @JvmField
        val defaultLockTimeout = AtomicInt(10_000)

        /**
         * The application wide default idle-transaction-timeout in milliseconds.
         * @since 3.0
         */
        @JvmField
        val defaultIdleTxTimeout = AtomicInt(60_000)

        /**
         * Returns the current application name.
         * @return the current application name.
         * @since 3.0
         */
        @JvmStatic
        @JsStatic
        fun appName(): String = currentContext().appName

        /**
         * Returns the current application identifier.
         * @return the current application identifier.
         * @since 3.0
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
         * Returns the current stream-information.
         * @return the current stream-information.
         */
        @JvmStatic
        @JsStatic
        fun streamInfo(): StreamInfo = currentContext().streamInfo

        /**
         * Returns the current stream-identifier.
         * @return the current stream-identifier.
         */
        @JvmStatic
        @JsStatic
        fun streamId(): String = currentContext().streamId

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
         * The default constructor to call to create [StreamInfo] instances, can be overridden by application code in bootstrap to ensure that all stream-information are some custom application specific instances.
         */
        @JvmStatic
        @JsStatic
        val streamInfoConstructorRef: Fn0<StreamInfo> = Fn0(::StreamInfo)

        /**
         * Can be overridden by application code to modify the thread local context gathering.
         */
        @JvmStatic
        @JsStatic
        var currentRef: Fn0<NakshaContext> = Fn0(threadLocal::get)

        /**
         * Creates and initializes a new [NakshaContext]. This method does not bind the new context to the current thread, if this is wanted, [attachToCurrentThread] should be called, like:
         * ```
         * val context = NakshaContext.newInstance("app","user").attachToCurrentThread()
         * ```
         * @param appId the application-id for which to create the context.
         * @param author the author.
         * @param streamId the stream-identifier to use, if _null_, a random identifier is generated.
         * @param su If the user is a permanent super-user.
         */
        // TODO: Kotlin-Compiler-Bug:
        //       We need open, otherwise Java can't create another static method with the same name in extending class!
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic
        @JsStatic
        @JvmOverloads
        open fun newInstance(appId: String, author: String? = null, streamId: String? = null, su: Boolean = false): NakshaContext {
            val context = constructorRef.call()
            context.appId = appId
            context.author = author
            if (streamId != null) context.streamId = streamId
            context.su = su
            return context
        }

        /**
         * Returns the current context from the current thread. If no context is yet attached, it creates a new context, and binds it to the current thread, returning it.
         * @return The context of the current thread.
         */
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic
        @JsStatic
        open fun currentContext(): NakshaContext = threadLocal.get()
    }
}

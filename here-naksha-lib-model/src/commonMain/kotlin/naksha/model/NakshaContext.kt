package naksha.model

import naksha.auth.UserRightsMatrix
import naksha.base.Int64
import naksha.base.PlatformThreadLocal
import naksha.base.Platform
import naksha.base.PlatformUtil
import naksha.base.fn.Fn0
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * The Naksha Context, a thread-local that stores credentials and other thread local information. The main purpose is to ensure that all
 * entities can perform authorization. It is normally created, when a new request is started, using the static [newInstance] factory method
 * and then attached to the current thread.
 * @since 2.0.5
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
open class NakshaContext protected constructor() {
    /**
     * The internal field of the **appId** getter and setters.
     */
    protected var _appId: String? = null

    /**
     * The application identifier of the client that acts. It is used at many places, for authorization, ownership of features and logging.
     */
    var appId: String
        get() = _appId ?: throw IllegalStateException("AppId must not be null")
        set(value) {
            _appId = value
        }

    /**
     * Changes the application-identifier and returns the [NakshaContext].
     * @param appId the new app-id.
     * @return this.
     */
    fun withAppId(appId: String): NakshaContext {
        this.appId = appId
        return this
    }

    /**
     * The internal field of the **streamId** setter and getter.
     */
    protected var _streamId: String? = null

    /**
     * The stream-identifier being used in logging to group log entries that belong to the same request.
     */
    open var streamId: String
        get() {
            var s =_streamId
            if (s == null) {
                s = PlatformUtil.randomString()
                _streamId = s
            }
            return s
        }
        set(value) { _streamId = value }

    /**
     * Changes the stream-id and returns the [NakshaContext].
     * @param streamId the new stream-id.
     * @return this.
     */
    fun withStreamId(streamId: String): NakshaContext {
        this.streamId = streamId
        return this
    }

    /**
     * The internal field of the **author** getter and setter.
     */
    protected var _author: String? = null

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
    fun withAuthor(author: String?): NakshaContext {
        this.author = author
        return this
    }

    /**
     * If the super-user flag is enabled. This normally is only done temporarily.
     * @since 2.0.7
     */
    var su: Boolean = false

    /**
     * The User-Rights-Matrix for authentication.
     * @since 2.0.16
     */
    var urm: UserRightsMatrix? = null

    /**
     * Changes the URM and returns the [NakshaContext].
     * @param urm the new User-Rights-Matrix.
     * @return this.
     * @since 2.0.16
     */
    fun withUrm(urm: UserRightsMatrix?): NakshaContext {
        this.urm = urm
        return this
    }

    /**
     * Returns the _actor_, which is normally the [author]. If author is _null_, then it returns the [appId].
     * @since 2.0.15
     */
    fun getActor(): String {
        return author ?: appId
    }

    /**
     * The epoch micro-second when the context was created. Is used in logging to log relative timestamps and can be used elsewhere for
     * relative timestamps (time since start of a request).
     */
    val startMicros: Int64 = Platform.currentMicros()

    /**
     * Attaches this context to the current thread.
     * @return this.
     */
    fun attachToCurrentThread(): NakshaContext {
        threadLocal.set(this)
        return this
    }

    @Suppress("OPT_IN_USAGE")
    companion object {
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

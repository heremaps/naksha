package naksha.model

import com.here.naksha.lib.base.com.here.naksha.lib.auth.UserRightsMatrix
import naksha.base.BaseThreadLocal
import naksha.base.Int64
import naksha.base.Platform
import naksha.model.NakshaContext.Fn
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * The Naksha Context, a thread-local that stores credentials and other thread local information.
 * @since 2.0.5
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
open class NakshaContext(val streamId: String? = null, var appId: String = "") {
    /**
     * Any interface needed for Java compatibility.
     */
    fun interface Fn {
        fun nakshaContext(): NakshaContext
    }

    fun interface Fn1 {
        fun nakshaContext(streamId: String?): NakshaContext
    }


//    private var _appId: String? = null
//
//    /**
//     * The application identifier.
//     */
//    open var appId: String
//        get() = _appId ?: throw IllegalStateException("AppId must not be null")
//        set(value) {
//            _appId = value
//        }


    fun withAppId(appId: String): NakshaContext {
        this.appId = appId
        return this
    }

    /**
     * The author.
     * @since 2.0.7
     */
    open var author: String? = null

    fun withAuthor(author: String?): NakshaContext {
        this.author = author
        return this
    }

    /**
     * If the super-user flag is enabled. This normally is only done temporarily.
     */
    open var su: Boolean = false

    /**
     * The User Rights Matrix for authentication.
     * @since 2.0.16
     */
    open var urm: UserRightsMatrix? = null

    fun withUrm(urm: UserRightsMatrix?): NakshaContext {
        this.urm = urm
        return this
    }

    /**
     * Returns the  value of author. If author is null then it returns the appId.
     * @since 2.0.15
     */
    fun getActor(): String {
        return author ?: appId
    }

    /**
     * The start nano time for time measurements.
     */
    open val startNanos: Int64 = Platform.currentNanos()

    @Suppress("OPT_IN_USAGE")
    companion object {
        private val threadLocal: BaseThreadLocal<NakshaContext> = Platform.newThreadLocal(::NakshaContext)

        /**
         * Can be overridden by application code to modify the context creation.
         */
        @JvmStatic
        @JsStatic
        var constructorRef: Fn1 = Fn1(::NakshaContext)

        /**
         * Can be overridden by application code to modify the thread local context gathering.
         */
        @JvmStatic
        @JsStatic
        var currentRef: Fn = Fn(threadLocal::get)

        /**
         * Creates a new Naksha Context.
         * @param appId The application-id for which to create the context.
         * @param author The author.
         * @param su If the user is a permanent super-user.
         */
        @JvmStatic
        @JsStatic
        open fun newInstance(streamId: String? = null, appId: String, author: String? = null, su: Boolean = false): NakshaContext {
            val context = constructorRef.nakshaContext(streamId)
            context.appId = appId
            context.author = author
            context.su = su
            return context
        }

        /**
         * Returns the current context. If no context exists, creates a new context and binds it to the thread-local.
         * Note that when a new context is created, any reading of the [appId] will raise a [IllegalStateException].
         * @return The current context.
         */
        @JvmStatic
        @JsStatic
        open fun currentContext(): NakshaContext = threadLocal.get()
    }
}
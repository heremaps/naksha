package naksha.model

import naksha.base.BaseThreadLocal
import naksha.base.Platform
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * The Naksha Context, a thread-local that stores credentials and other thread local information.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
open class NakshaContext protected constructor() {
    /**
     * Any interface needed for Java compatibility.
     */
    fun interface Fn {
        fun nakshaContext(): NakshaContext
    }

    private var _appId: String? = null

    /**
     * The application identifier.
     */
    open var appId: String
        get() = _appId ?: throw IllegalStateException("AppId must not be null")
        set(value) {
            _appId = value
        }

    /**
     * The author.
     */
    open var author: String? = null

    /**
     * If the super-user flag is enabled. This normally is only done temporarily.
     */
    open var su: Boolean = false

    // TODO: Add URM

    @Suppress("OPT_IN_USAGE")
    companion object {
        private val threadLocal: BaseThreadLocal<NakshaContext> = Platform.newThreadLocal(::NakshaContext)

        /**
         * Can be overridden by application code to modify the context creation.
         */
        @JvmStatic
        @JsStatic
        var constructorRef: Fn = Fn(::NakshaContext)

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
        open fun newInstance(appId: String, author: String? = null, su: Boolean = false): NakshaContext {
            val context = constructorRef.nakshaContext()
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
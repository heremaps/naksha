package naksha.model

import naksha.base.BaseThreadLocal
import naksha.base.Platform
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

// FIXME TODO move it to proper library

@OptIn(ExperimentalJsExport::class)
@JsExport
open class NakshaContext private constructor() {
    private var _appId: String? = null

    open var appId: String
        get() = _appId ?: throw IllegalStateException("AppId must not be null")
        set(value) {
            _appId = value
        }

    open var author: String? = null

    open var su: Boolean = false


    @Suppress("OPT_IN_USAGE")
    companion object {
        private val threadLocal: BaseThreadLocal<NakshaContext> = Platform.newThreadLocal(::NakshaContext)

        /**
         * Can be overridden by application code to modify the context creation.
         */
        @JvmStatic
        @JsStatic
        var constructorRef: () -> NakshaContext = ::NakshaContext

        /**
         * Can be overridden by application code to modify the thread local context gathering.
         */
        @JvmStatic
        @JsStatic
        var currentRef: () -> NakshaContext = threadLocal::get

        @JvmStatic
        @JsStatic
        fun create(appId: String, author: String? = null, su: Boolean = false): NakshaContext {
            val context = constructorRef()
            context.appId = appId
            context.author = author
            context.su = su
            return context
        }

        @JvmStatic
        @JsStatic
        fun current(): NakshaContext = threadLocal.get()
    }
}
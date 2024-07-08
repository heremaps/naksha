package naksha.model

import naksha.base.NotNullProperty
import naksha.base.ObjectProxy
import naksha.base.Platform
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

@Suppress("OPT_IN_USAGE")
@JsExport
open class NakshaPropertiesProxy : ObjectProxy() {
    companion object {
        @JvmField
        @JsStatic
        val XYZ_KEY = Platform.intern("@ns:com:here:xyz")

        private val XYZ = NotNullProperty<Any, NakshaPropertiesProxy, XyzProxy>(XyzProxy::class, name = XYZ_KEY) { _, _ -> XyzProxy() }
    }

    /**
     * The XYZ namespace, a must in all Naksha features (`@ns:com:here:xyz`).
     */
    var xyz by XYZ
}
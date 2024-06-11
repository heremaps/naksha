@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.NullableProperty
import naksha.base.P_Object
import naksha.base.Platform
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@JsExport
open class NakshaPropertiesProxy : P_Object() {
    companion object {
        @JvmStatic
        val XYZ_KEY = Platform.intern("@ns:com:here:xyz")

        private val XYZ = NullableProperty<Any, NakshaPropertiesProxy, XyzProxy>(
            XyzProxy::class, name = XYZ_KEY
        )
    }

    var xyz: XyzProxy? by XYZ
}
package naksha.model

import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.ObjectProxy
import naksha.base.Platform
import naksha.model.XyzProxy.Companion.DELTA_PROXY
import naksha.model.XyzProxy.Companion.REFERENCES
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
        private val DELTA_PROXY = NullableProperty<Any, XyzProxy, NakshaDeltaProxy>(NakshaDeltaProxy::class)
        private val REFERENCES = NullableProperty<Any, XyzProxy, XyzReferencesProxy>(XyzReferencesProxy::class)
    }

    /**
     * The XYZ namespace, a must in all Naksha features (`@ns:com:here:xyz`).
     */
    var xyz by XYZ


    var deltaProxy: NakshaDeltaProxy? by DELTA_PROXY

    /**
     * References to MOM objects.
     */
    var references: XyzReferencesProxy? by REFERENCES

    fun useDeltaNamespace(): NakshaDeltaProxy {
        var deltaProxy = this.deltaProxy
        if (deltaProxy == null) {
            deltaProxy = NakshaDeltaProxy()
            this.deltaProxy = deltaProxy
        }
        return deltaProxy
    }
}
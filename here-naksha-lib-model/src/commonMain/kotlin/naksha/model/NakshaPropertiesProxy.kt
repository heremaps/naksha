package naksha.model

import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.AnyObject
import naksha.base.Platform
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

@Suppress("OPT_IN_USAGE")
@JsExport
open class NakshaPropertiesProxy : AnyObject() {
    companion object {
        @JvmField
        @JsStatic
        val XYZ_KEY = Platform.intern("@ns:com:here:xyz")

        private val XYZ = NotNullProperty<Any, NakshaPropertiesProxy, XyzNs>(XyzNs::class, name = XYZ_KEY) { _, _ -> XyzNs() }
        private val DELTA_PROXY = NullableProperty<Any, NakshaPropertiesProxy, NakshaDeltaProxy>(NakshaDeltaProxy::class)
        private val REFERENCES = NullableProperty<Any, NakshaPropertiesProxy, XyzReferencesProxy>(XyzReferencesProxy::class)
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
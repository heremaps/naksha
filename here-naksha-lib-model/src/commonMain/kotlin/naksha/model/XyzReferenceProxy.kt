package naksha.model

import naksha.base.NullableProperty
import naksha.base.ObjectProxy
import kotlin.js.JsExport

/**
 * Xyz reference object holding minimum equivalent fields from MOM reference object
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class XyzReferenceProxy : ObjectProxy() {

    companion object XyzReferenceCompanion {
        private val STRING_NULL = NullableProperty<Any, XyzReferenceProxy, String>(String::class)
    }

    var id: String? by STRING_NULL
    var spaceId: String? by STRING_NULL
    var featureType: String? by STRING_NULL
}
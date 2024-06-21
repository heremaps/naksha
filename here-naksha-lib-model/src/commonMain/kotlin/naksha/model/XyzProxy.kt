@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.P_Object
import kotlin.js.JsExport

/**
 * The XYZ namespace stored in the `@ns:com:here:xyz` property of the [NakshaFeatureProxy].
 */
@JsExport
class XyzProxy : P_Object() {
    companion object {
        private val ACTION = NullableProperty<Any, XyzProxy, String>(String::class, defaultValue = { XYZ_EXEC_CREATED })
        private val UUID = NullableProperty<Any, XyzProxy, String>(String::class)
        private val FNVA1 = NullableProperty<Any, XyzProxy, String>(String::class)
        private val VERSION = NullableProperty<Any, XyzProxy, Int>(Int::class)
        private val GEO_GRID = NullableProperty<Any, XyzProxy, Int>(Int::class)
        private val ORIGIN = NullableProperty<Any, XyzProxy, String>(String::class)
        private val APP_ID = NullableProperty<Any, XyzProxy, String>(String::class)
        private val AUTHOR = NullableProperty<Any, XyzProxy, String>(String::class)
        private val TIMESTAMP = NullableProperty<Any, XyzProxy, Int64>(Int64::class)
        private val TAGS = NotNullProperty<Any, XyzProxy, TagsProxy>(
            TagsProxy::class)
    }

    /**
     * FIXME not sure if we need it here since we keep it on flags
     */
    var action: String? by ACTION
    var uuid: String? by UUID
    var uuidNext: String? by UUID
    var puuid: String? by UUID
    var version: Int? by VERSION
    var appId: String? by APP_ID
    var author: String? by AUTHOR
    var fnva1: String? by FNVA1
    var origin: String? by ORIGIN
    var tags: TagsProxy by TAGS
    var createdAt: Int64? by TIMESTAMP
    var updatedAt: Int64? by TIMESTAMP
    var authorTs: Int64? by TIMESTAMP
    var geoGrid: Int? by GEO_GRID
}
@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.MapProxy
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import naksha.base.String_TYPE
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A map between the map-id and the details about what changed in this map within the transaction.
 * @since 3.0
 */
@JsExport
class NakshaTxMapById : MapProxy<String, NakshaTxMap>(String_TYPE, NakshaTxMap.TYPE) {
    companion object NakshaTxMapById_C {
        /**
         * The [PlatformType] of [NakshaTxMapById].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(NakshaTxMapById::class).withPackageName(PACKAGE_NAME)
    }
}

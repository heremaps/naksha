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
 * A map between the collection identifier, and details information about what changed within the collection.
 * @since 3.0
 */
@JsExport
class NakshaTxCollectionById : MapProxy<String, NakshaTxCollection>(String_TYPE, NakshaTxCollection.TYPE) {
    companion object NakshaTxCollectionById_C {
        /**
         * The [PlatformType] of [NakshaTxCollectionById].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(NakshaTxCollectionById::class).withPackageName(PACKAGE_NAME)
    }
}
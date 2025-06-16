@file:Suppress("OPT_IN_USAGE")

package naksha.auth.naksha

import naksha.auth.ServiceOpParamsList
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A list of [CollectionParams].
 * @since 3.0
 * @see CollectionParams
 * @see NakshaOps.createCollections
 * @see NakshaOps.readCollections
 * @see NakshaOps.updateCollections
 * @see NakshaOps.deleteCollections
 */
@JsExport
class CollectionParamsList : ServiceOpParamsList<CollectionParams>(CollectionParams.TYPE) {
    companion object CollectionParamsList_C {
        /**
         * The [PlatformType] of [CollectionParamsList].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<CollectionParamsList> = forKClass(CollectionParamsList::class).withPackageName(PACKAGE_NAME)
    }
}
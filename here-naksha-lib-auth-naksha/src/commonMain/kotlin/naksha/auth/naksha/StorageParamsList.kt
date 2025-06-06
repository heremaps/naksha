@file:Suppress("OPT_IN_USAGE")

package naksha.auth.naksha

import naksha.auth.ServiceOpParamsList
import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A list of [StorageParams].
 * @since 3.0
 * @see NakshaOps.useStorages
 * @see NakshaOps.manageStorages
 */
@JsExport
class StorageParamsList : ServiceOpParamsList<StorageParams>(StorageParams.TYPE) {
    companion object StorageResourceListCompanion {
        /**
         * The [PlatformType] of [StorageParamsList].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<StorageParamsList> = forKClass(StorageParamsList::class).withPackageName(PACKAGE_NAME)
    }
}
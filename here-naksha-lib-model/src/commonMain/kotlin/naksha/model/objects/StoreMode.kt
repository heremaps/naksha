@file:Suppress("OPT_IN_USAGE")
package naksha.model.objects

import naksha.base.PlatformEnum
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * How the data should be stored for certain components of [NakshaCollection].
 * - [ON] data should be stored.
 * - [SUSPEND] temporarily disable storage, can be re-enabled later, keeps older data available.
 * - [OFF] disable data storage permanently, wipe stored older data.
 * @since 3.0
 */
@JsExport
class StoreMode: PlatformEnum() {
    companion object StoreMode_C {
        /**
         * The [PlatformType] of [StoreMode].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(StoreMode::class).withPackageName(PACKAGE_NAME)

        /**
         * The default storage-mode, data should be stored.
         * @since 3.0
         */
        @JvmField
        val ON = defIgnoreCase(TYPE, "on")

        /**
         * Temporarily disable storage, can be re-enabled later, keeps older data available.
         * @since 3.0
         */
        @JvmField
        val SUSPEND = defIgnoreCase(TYPE, "suspend")

        /**
         * Disable data storage permanently, wipe stored older data.
         * @since 3.0
         */
        @JvmField
        val OFF = defIgnoreCase(TYPE, "off")
    }

    override fun namespace() = TYPE
    override fun initClass() {}
}
@file:Suppress("OPT_IN_USAGE")
package naksha.model.objects

import naksha.base.BaseEnum
import kotlin.js.JsExport
import kotlin.jvm.JvmField
import kotlin.reflect.KClass

/**
 * How the data should be stored for certain components of [NakshaCollection].
 * - [ON] data should be stored.
 * - [SUSPEND] temporarily disable storage, can be re-enabled later, keeps older data available.
 * - [OFF] disable data storage permanently, wipe stored older data.
 * @since 3.0.0
 */
@JsExport
class StoreMode: BaseEnum() {
    companion object StoreMode_C {
        /**
         * The default storage-mode, data should be stored.
         * @since 3.0.0
         */
        @JvmField
        val ON = defIgnoreCase(StoreMode::class, "on")

        /**
         * Temporarily disable storage, can be re-enabled later, keeps older data available.
         * @since 3.0.0
         */
        @JvmField
        val SUSPEND = defIgnoreCase(StoreMode::class, "suspend")

        /**
         * Disable data storage permanently, wipe stored older data.
         * @since 3.0.0
         */
        @JvmField
        val OFF = defIgnoreCase(StoreMode::class, "off")
    }

    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out BaseEnum> = StoreMode::class

    override fun initClass() {}
}
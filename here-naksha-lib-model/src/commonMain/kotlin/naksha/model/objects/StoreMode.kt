@file:Suppress("OPT_IN_USAGE")
package naksha.model.objects

import naksha.base.JsEnum
import kotlin.js.JsExport
import kotlin.jvm.JvmField
import kotlin.reflect.KClass

/**
 * How the data should be stored for certain components of [NakshaCollection].
 * [ON] data should be stored.
 * [SUSPEND] newer data should not be collected, older data still available.
 * [OFF] all data is wiped and no new data collected.
 */
@JsExport
class StoreMode: JsEnum() {
    companion object StoreMode_C {
        @JvmField
        val ON = defIgnoreCase(StoreMode::class, "on")

        @JvmField
        val SUSPEND = defIgnoreCase(StoreMode::class, "suspend")

        @JvmField
        val OFF = defIgnoreCase(StoreMode::class, "off")
    }

    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = StoreMode::class

    override fun initClass() {}
}
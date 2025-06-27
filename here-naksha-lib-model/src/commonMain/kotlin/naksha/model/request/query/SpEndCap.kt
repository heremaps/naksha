@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.PlatformEnum
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * TODO
 *
 * @since 3.0
 * @see SpBuffer
 */
@JsExport
class SpEndCap : PlatformEnum() {
    companion object SpEndCap_C {
        /**
         * The [PlatformType] of [SpEndCap].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(SpEndCap::class).withPackageName(PACKAGE_NAME)

        val ROUND = def(TYPE, "round")
        val BUTT = def(TYPE, "butt").alias<SpEndCap>("flat")
    }

    override fun namespace() = TYPE
    override fun initClass() {}
}

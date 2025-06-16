@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.JsEnum
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.reflect.KClass

/**
 * @since 3.0
 * @see SpBuffer
 */
@JsExport
class SpSide : JsEnum() {
    companion object SpSide_C {
        /**
         * The [PlatformType] of [SpSide].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(SpSide::class).withPackageName(PACKAGE_NAME)

        val BOTH = def(TYPE, "both")
        val LEFT = def(TYPE, "left")
        val RIGHT = def(TYPE, "right")
    }

    override fun namespace() = TYPE
    override fun initClass() {}
}
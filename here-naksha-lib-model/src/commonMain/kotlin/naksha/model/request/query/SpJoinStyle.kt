@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.PlatformEnum
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The join style.
 * @since 3.0
 * @see SpBuffer
 */
@JsExport
class SpJoinStyle : PlatformEnum() {
    companion object SpJoinStyle_C {
        /**
         * The [PlatformType] of [SpJoinStyle].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(SpJoinStyle::class).withPackageName(PACKAGE_NAME)

        val ROUND = def(TYPE, "round")
        val MITRE = def(TYPE, "mitre")
        val BEVEL = def(TYPE, "bevel")
    }

    override fun namespace() = TYPE
    override fun initClass() {}
}
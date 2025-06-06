@file:Suppress("OPT_IN_USAGE")

package naksha.diff

import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.math.round

/**
 * The default implementation of the [DiffContext].
 *
 * Can be extended, to just implement a special handling for [ignore], but leave the default [equalsDouble] unmodified.
 *
 * @since 3.0
 */
@JsExport
open class DefaultDiffContext protected constructor() : DiffContext {
    companion object DefaultDiffContext_C {
        /**
         * The [PlatformType] of [DefaultDiffContext].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(DefaultDiffContext::class).withPackageName(PACKAGE_NAME)

        /**
         * The default instance _(singleton)_.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val INSTANCE: DefaultDiffContext = DefaultDiffContext()
    }

    override fun ignore(key: Any, sourceMap: Map<*, *>, targetOrPatchMap: Map<*, *>): Boolean = false

    override fun equalsDouble(first: Double, second: Double): Boolean = round(first * 1e6) - round(second * 1e6) == 0.0
}
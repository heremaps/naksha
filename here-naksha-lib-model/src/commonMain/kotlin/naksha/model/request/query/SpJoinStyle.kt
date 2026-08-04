@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.BaseEnum
import kotlin.js.JsExport
import kotlin.reflect.KClass

/**
 * The join style.
 */
@JsExport
class SpJoinStyle : BaseEnum() {
    companion object SpJoinStyle_C {
        val ROUND = def(SpJoinStyle::class, "round")
        val MITRE = def(SpJoinStyle::class, "mitre")
        val BEVEL = def(SpJoinStyle::class, "bevel")
    }

    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out BaseEnum> = SpJoinStyle::class

    override fun initClass() {}
}
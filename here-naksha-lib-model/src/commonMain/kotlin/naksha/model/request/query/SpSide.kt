@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.BaseEnum
import kotlin.js.JsExport
import kotlin.reflect.KClass

@JsExport
class SpSide : BaseEnum() {
    companion object SpSide_C {
        val BOTH = def(SpSide::class, "both")
        val LEFT = def(SpSide::class, "left")
        val RIGHT = def(SpSide::class, "right")
    }

    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out BaseEnum> = SpSide::class

    override fun initClass() {}
}
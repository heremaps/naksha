@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.JsEnum
import kotlin.js.JsExport
import kotlin.reflect.KClass

@JsExport
class SpEndCap : JsEnum() {
    companion object SpEndCap_C {
        val ROUND = def(SpEndCap::class, "round")
        val BUTT = def(SpEndCap::class, "butt").alias<SpEndCap>("flat")
    }

    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = SpEndCap::class

    override fun initClass() {}
}

package naksha.model.response

import naksha.base.JsEnum
import kotlin.js.JsExport
import kotlin.reflect.KClass

@Suppress("OPT_IN_USAGE")
@JsExport
class ExecutedOp : JsEnum() {
    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = ExecutedOp::class

    override fun initClass() {
    }

    companion object {
        val READ = defIgnoreCase(ExecutedOp::class, "READ")
        val CREATED = defIgnoreCase(ExecutedOp::class, "CREATED")
        val UPDATED = defIgnoreCase(ExecutedOp::class, "UPDATED")
        val DELETED = defIgnoreCase(ExecutedOp::class, "DELETED")
        val PURGED = defIgnoreCase(ExecutedOp::class, "PURGED")
    }
}

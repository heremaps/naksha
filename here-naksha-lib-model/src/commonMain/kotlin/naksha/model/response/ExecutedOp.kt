package naksha.model.response

import naksha.base.JsEnum
import kotlin.js.JsExport
import kotlin.jvm.JvmField
import kotlin.reflect.KClass

@Suppress("OPT_IN_USAGE")
@JsExport
class ExecutedOp : JsEnum() {
    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = ExecutedOp::class

    override fun initClass() {
    }

    companion object {
        @JvmField
        val READ = defIgnoreCase(ExecutedOp::class, "READ")
        @JvmField
        val CREATED = defIgnoreCase(ExecutedOp::class, "CREATED")
        @JvmField
        val UPDATED = defIgnoreCase(ExecutedOp::class, "UPDATED")
        @JvmField
        val DELETED = defIgnoreCase(ExecutedOp::class, "DELETED")
        @JvmField
        val PURGED = defIgnoreCase(ExecutedOp::class, "PURGED")
    }
}

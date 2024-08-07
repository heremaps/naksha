@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.JsEnum
import kotlin.js.JsExport
import kotlin.jvm.JvmField
import kotlin.reflect.KClass

/**
 * An enumeration about what was done by the storage.
 */
@JsExport
class ExecutedOp : JsEnum() {
    companion object ExecutedOp_C {
        /**
         * A read was performed.
         */
        @JvmField
        val READ = defIgnoreCase(ExecutedOp::class, "READ")

        /**
         * A creation was performed.
         */
        @JvmField
        val CREATED = defIgnoreCase(ExecutedOp::class, "CREATED")

        /**
         * An update was performed.
         */
        @JvmField
        val UPDATED = defIgnoreCase(ExecutedOp::class, "UPDATED")

        /**
         * A delete was performed.
         */
        @JvmField
        val DELETED = defIgnoreCase(ExecutedOp::class, "DELETED")

        /**
         * A purge was performed, that means a deletion, and a removal from the deletion table.
         */
        @JvmField
        val PURGED = defIgnoreCase(ExecutedOp::class, "PURGED")

        /**
         * Nothing was done, even while a modification was requested, this happens because the object was already in the desired state. For example, when a deletion is requested, but the object does not exist.
         */
        @JvmField
        val RETAINED = defIgnoreCase(ExecutedOp::class, "RETAINED")
    }

    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = ExecutedOp::class

    override fun initClass() {}
}

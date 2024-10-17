@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.JsEnum
import naksha.model.ACTION_CREATE
import naksha.model.ACTION_DELETE
import naksha.model.ACTION_UPDATE
import naksha.model.Action
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
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

        /**
         * Returns an [ExecutedOp] from the given action.
         * @param action the action.
         * @return the best matching [ExecutedOp].
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        @JsName("fromActionEnum")
        fun fromAction(action: Action): ExecutedOp = when(action) {
            Action.CREATED -> CREATED
            Action.UPDATED -> UPDATED
            Action.DELETED -> DELETED
            else -> READ
        }

        /**
         * Returns an [ExecutedOp] from the given action.
         * @param action the action.
         * @return the best matching [ExecutedOp].
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun fromAction(action: Int): ExecutedOp = when(action) {
            ACTION_CREATE -> CREATED
            ACTION_UPDATE -> UPDATED
            ACTION_DELETE -> DELETED
            else -> READ
        }
    }

    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = ExecutedOp::class

    override fun initClass() {}
}

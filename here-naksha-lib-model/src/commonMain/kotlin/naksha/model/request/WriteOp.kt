@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.JsEnum
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.reflect.KClass

@JsExport
class WriteOp : JsEnum(), Comparable<WriteOp> {
    companion object WriteOp_C {
        /**
         * When the write operation is _null_, this is an invalid state that should not persist in a [Write].
         */
        @JvmField
        @JsStatic
        val NULL = def(WriteOp::class, null)

        /**
         * Create the feature, fail if the feature exists already.
         */
        @JvmField
        @JsStatic
        val CREATE = defIgnoreCase(WriteOp::class, "CREATE") { self -> self.order = 0 }

        /**
         * Update or created the feature, should never fail.
         */
        @JvmField
        @JsStatic
        val UPSERT = defIgnoreCase(WriteOp::class, "UPSERT") { self -> self.order = 1 }

        /**
         * Update the feature, fail if the feature does not exist.
         */
        @JvmField
        @JsStatic
        val UPDATE = defIgnoreCase(WriteOp::class, "UPDATE") { self -> self.order = 2 }

        /**
         * Delete the feature, does not fail normally, even when the feature does not exist.
         */
        @JvmField
        @JsStatic
        val DELETE = defIgnoreCase(WriteOp::class, "DELETE") { self -> self.order = 3 }

        /**
         * Delete the feature, and remove remainders from the shadow delete table, so delete fully.
         *
         * This operation is important for _views_, where a _PURGE_ will restore the underlying version of a feature from lower layers, while a normal _DELETE_ would cause the feature to disappear. It is strongly recommended to only execute a _PURGE_, when being in a view context and the behavior that results from it is explicitly wished.
         */
        @JvmField
        @JsStatic
        val PURGE = defIgnoreCase(WriteOp::class, "PURGE") { self -> self.order = 4 }
    }

    /**
     * An ordering number, defaults to 100 (so order at the end).
     */
    var order: Int = 100
        private set

    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = WriteOp::class

    override fun initClass() {}

    // We want to order by: CREATE, UPSERT, UPDATE, DELETE, PURGE
    override fun compareTo(other: WriteOp): Int = order.compareTo(other.order)
}
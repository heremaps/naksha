@file:OptIn(ExperimentalJsExport::class, ExperimentalJsStatic::class)

package naksha.model.request.ops

import naksha.base.AnyObject
import naksha.base.MapProxy
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import kotlin.js.ExperimentalJsExport
import kotlin.js.ExperimentalJsStatic
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * A member operation.
 * @since 3.0
 */
@JsExport
open class Op : AnyObject() {
    companion object MemberOp_C {
        private val STRING = NotNullProperty<Op, String>(String::class)
        private val STRING_OR_NULL = NullableProperty<Op, String>(String::class)

        const val AND = "and"
        const val OR = "or"
        const val NOT = "not"

        /**
         * Auto-detect the concrete type of member operation and return the cast real type.
         * @param op the member-operation to detect the real type.
         * @return the real [Op] instance or just `null`, if no real type is known.
         */
        @JvmStatic
        @JsStatic
        fun detect(op: MapProxy<*,*>): Op? = when(op.getRaw("op") as String?) {
            AND -> op.proxy(And::class)
            OR -> op.proxy(Or::class)
            NOT -> op.proxy(Not::class)
            else -> null
        }
    }

    /**
     * The operation identifier.
     */
    var op: String by STRING

    /**
     * The name of the member to query; if any _(some operations do not work upon members)_.
     */
    var at: String? by STRING_OR_NULL
}
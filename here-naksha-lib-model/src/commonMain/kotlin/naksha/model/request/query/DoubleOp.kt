@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A subset of the [query operations][AnyOp], limited to numeric compares.
 * @since 3.0.0
 */
@JsExport
class DoubleOp : AnyOp() {
    companion object QNumericOpCompanion {
        /**
         * Tests if the field value is a number, and equals to the parameter value.
         * @since 3.0.0
         */
        @JvmField
        @JsStatic
        val EQ = def(DoubleOp::class, "eq")

        /**
         * Tests if the field value is either not a number, or does not equal to the parameter value.
         * @since 3.0.0
         */
        @JvmField
        @JsStatic
        val NE = def(DoubleOp::class, "ne")

        /**
         * Tests if the field value is a number, and is greater than the parameter value.
         * @since 3.0.0
         */
        @JvmField
        @JsStatic
        val GT = def(DoubleOp::class, "gt")

        /**
         * Tests if the field value is a number, and is greater than or equal to the parameter value.
         * @since 3.0.0
         */
        @JvmField
        @JsStatic
        val GTE = def(DoubleOp::class, "gte")

        /**
         * Tests if the field value is a number, and is less than the parameter value.
         * @since 3.0.0
         */
        @JvmField
        @JsStatic
        val LT = def(DoubleOp::class, "lt")

        /**
         * Tests if the field value is a number, and is less than or equal to the parameter value.
         * @since 3.0.0
         */
        @JvmField
        @JsStatic
        val LTE = def(DoubleOp::class, "lte")
    }
}
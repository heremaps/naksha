@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.condition

import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A subset of the [query operations][QueryOp], limited to numeric compares.
 */
@JsExport
class QueryNumber : QueryOp() {
    companion object QNumericOpCompanion {
        /**
         * Tests if the field value is a number, and equals to the parameter value.
         */
        @JvmField
        @JsStatic
        val EQ = def(QueryNumber::class, "eq")

        /**
         * Tests if the field value is a number, and is greater than the parameter value.
         */
        @JvmField
        @JsStatic
        val GT = def(QueryNumber::class, "gt")

        /**
         * Tests if the field value is a number, and is greater than or equal to the parameter value.
         */
        @JvmField
        @JsStatic
        val GTE = def(QueryNumber::class, "gte")

        /**
         * Tests if the field value is a number, and is less than the parameter value.
         */
        @JvmField
        @JsStatic
        val LT = def(QueryNumber::class, "lt")

        /**
         * Tests if the field value is a number, and is less than or equal to the parameter value.
         */
        @JvmField
        @JsStatic
        val LTE = def(QueryNumber::class, "lte")
    }
}
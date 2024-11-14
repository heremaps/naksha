@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A subset of the [query operations][AnyOp], limited to string compares.
 * @since 3.0.0
 */
@JsExport
class StringOp : AnyOp() {
    companion object QStringOpCompanion {
        /**
         * Tests if the field value is a string, and equals the given parameter value.
         */
        @JvmField
        @JsStatic
        val EQUALS = def(StringOp::class, "equals")

        /**
         * Tests if the field value is not a string, or does not equal to the given parameter value.
         */
        @JvmField
        @JsStatic
        val NOT_EQUALS = def(StringOp::class, "not_equals")

        /**
         * Tests if the field value is a string, and starts with the given parameter value.
         */
        @JvmField
        @JsStatic
        val STARTS_WITH = def(StringOp::class, "startsWith")

    }
}
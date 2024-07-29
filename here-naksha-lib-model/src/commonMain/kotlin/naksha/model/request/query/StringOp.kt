@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * All string operations.
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
         * Tests if the field value is a string, and starts with the given parameter value.
         */
        @JvmField
        @JsStatic
        val STARTS_WITH = def(StringOp::class, "startsWith")

    }
}
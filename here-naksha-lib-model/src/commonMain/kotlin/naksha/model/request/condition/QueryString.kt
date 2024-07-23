@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.condition

import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * All string operations.
 */
@JsExport
class QueryString : QueryOp() {
    companion object QStringOpCompanion {
        /**
         * Tests if the field value is a string, and equals the given parameter value.
         */
        @JvmField
        @JsStatic
        val EQUALS = def(QueryString::class, "equals")

        /**
         * Tests if the field value is a string, and starts with the given parameter value.
         */
        @JvmField
        @JsStatic
        val STARTS_WITH = def(QueryString::class, "startsWith")

    }
}
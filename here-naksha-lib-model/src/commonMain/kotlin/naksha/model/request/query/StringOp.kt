@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A subset of the [query operations][AnyOp], limited to string compares.
 * @since 3.0
 * @see AnyOp
 * @see DoubleOp
 * @see StringOp
 */
@JsExport
class StringOp : AnyOp() {
    companion object StringOp_C {
        /**
         * The [PlatformType] of [StringOp].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(StringOp::class).withPackageName(PACKAGE_NAME)

        /**
         * Tests if the field value is a string, and equals the given parameter value.
         */
        @JvmField
        @JsStatic
        val EQUALS = def(TYPE, "equals")

        /**
         * Tests if the field value is not a string, or does not equal to the given parameter value.
         */
        @JvmField
        @JsStatic
        val NOT_EQUALS = def(TYPE, "not_equals")

        /**
         * Tests if the field value is a string, and starts with the given parameter value.
         */
        @JvmField
        @JsStatic
        val STARTS_WITH = def(TYPE, "startsWith")

    }
}
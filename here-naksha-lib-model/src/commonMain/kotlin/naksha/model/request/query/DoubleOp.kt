@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A subset of the [query operations][AnyOp], limited to numeric compares.
 * @since 3.0
 * @see AnyOp
 * @see DoubleOp
 * @see StringOp
 */
@JsExport
class DoubleOp : AnyOp() {
    companion object DoubleOp_C {
        /**
         * The [PlatformType] of [DoubleOp].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(DoubleOp::class).withPackageName(PACKAGE_NAME)

        /**
         * Tests if the field value is a number, and equals to the parameter value.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val EQ = def(TYPE, "eq")

        /**
         * Tests if the field value is either not a number, or does not equal to the parameter value.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val NE = def(TYPE, "ne")

        /**
         * Tests if the field value is a number, and is greater than the parameter value.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val GT = def(TYPE, "gt")

        /**
         * Tests if the field value is a number, and is greater than or equal to the parameter value.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val GTE = def(TYPE, "gte")

        /**
         * Tests if the field value is a number, and is less than the parameter value.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val LT = def(TYPE, "lt")

        /**
         * Tests if the field value is a number, and is less than or equal to the parameter value.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val LTE = def(TYPE, "lte")
    }
}
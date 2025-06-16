@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.Double_TYPE
import naksha.base.NotNullEnum
import naksha.base.NotNullProperty
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * Test if the value of the tag is a double, and matches the given operation against the given value.
 * @since 3.0
 * @see IQuery
 * @see ITagQuery
 * @see TagQuery
 */
@JsExport
class TagValueIsDouble() : TagQuery() {

    /**
     * Test if the value of the tag is a double, and matches the given operation against the given value.
     *
     * @param name the tag name.
     * @param op if the value is a double, the operation to be used to compare against the given value.
     * @param value the value against which to compare using the given op.
     * @since 3.0
     */
    @JsName("of")
    constructor(name: String, op: DoubleOp, value: Double) : this() {
        this.name = name
        this.op = op
        this.value = value
    }

    companion object TagValueIsDouble_C {
        /**
         * The [PlatformType] of [TagValueIsDouble].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(TagValueIsDouble::class).withPackageName(PACKAGE_NAME)

        private val QUERY_NUMBER = NotNullEnum<TagValueIsDouble, DoubleOp>(DoubleOp.TYPE) { _, _ -> DoubleOp.EQ }
        private val DOUBLE = NotNullProperty<TagValueIsDouble, Double>(Double_TYPE) { _, _ -> 0.0 }
    }

    /**
     * The operation to execute.
     * @since 3.0
     */
    var op by QUERY_NUMBER

    /**
     * The target value for the operation.
     * @since 3.0
     */
    var value by DOUBLE
}
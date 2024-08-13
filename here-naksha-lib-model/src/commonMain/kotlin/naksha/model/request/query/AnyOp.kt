@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.JsEnum
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass

/**
 * A query operation.
 * @since 3.0.0
 */
@JsExport
open class AnyOp : JsEnum() {
    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = AnyOp::class

    override fun initClass() {
        register(DoubleOp::class)
        register(StringOp::class)
    }

    companion object QOpCompanion {
        /**
         * Tests if the property exists.
         * @since 3.0.0
         */
        @JsStatic
        @JvmField
        val EXISTS = def(AnyOp::class, "exists")

        /**
         * Tests if the field value equals to any of the given values, requires the parameter value to be an [Array].
         * @since 3.0.0
         */
        @JsStatic
        @JvmField
        val IS_ANY_OF = def(AnyOp::class, "anyOf")

        /**
         * Tests if the field value is _null_.
         * @since 3.0.0
         */
        @JsStatic
        @JvmField
        val IS_NULL = def(AnyOp::class, "isNull")

        /**
         * Tests if the field value is not _null_.
         * @since 3.0.0
         */
        @JsStatic
        @JvmField
        val IS_NOT_NULL = def(AnyOp::class, "isNotNull")

        /**
         * Tests if the field value is explicitly _true_.
         * @since 3.0.0
         */
        @JsStatic
        @JvmField
        val IS_TRUE = def(AnyOp::class, "isTrue")

        /**
         * Tests if the field value is explicitly _false_.
         * @since 3.0.0
         */
        @JsStatic
        @JvmField
        val IS_FALSE = def(AnyOp::class, "isFalse")

        /**
         * Performs in property inspection.
         *
         * - If the property is an array, then the provided value also has to be an array (`["value"]`).
         * - If the property is an object, then the provided value also has to be an object (`{"prop":"value"}`).
         * - If the property is a primitive, then the provided value also has to be a primitive (`"value"`).
         *
         * Only top level values search are supported. For json:
         * ```
         * {
         *   "type": "Feature",
         *   "properties": {
         *     "reference": [
         *       {"id": "106003684", "prop":{"a":1}}
         *     ]
         *   }
         * }
         * ```
         * You can query path `["properties","reference"]` by direct children: `[{"id":"106003684"}]` and `[{"prop":{"a":1}}]`, but querying by sub property that is not direct child won't work: `{"a":1}`.
         *
         * Also have in mind that provided [Property] can't contain array properties in the middle of path. Array property are allowed only as last element of path. This is correct: `properties -> reference`, and this is not correct: `properties -> reference -> id`, because `reference` is an array.
         * @since 3.0.0
         */
        @JsStatic
        @JvmField
        val CONTAINS = def(AnyOp::class, "contains")
    }
}
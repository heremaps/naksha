@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.condition

import naksha.base.JsEnum
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass

/**
 * The query operations.
 */
@JsExport
open class QueryOp : JsEnum() {
    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = QueryOp::class

    override fun initClass() {
        register(QueryNumber::class)
        register(QueryString::class)
    }

    companion object QOpCompanion {
        /**
         * Tests if the property exists.
         */
        @JsStatic
        @JvmStatic
        val EXISTS = def(QueryOp::class, "exists")

        /**
         * Tests if the field value equals to any of the given values, requires the parameter value to be an [Array].
         */
        @JsStatic
        @JvmStatic
        val IS_ANY_OF = def(QueryOp::class, "anyOf")

        /**
         * Tests if the field value is _null_.
         */
        @JsStatic
        @JvmStatic
        val IS_NULL = def(QueryOp::class, "isNull")

        /**
         * Tests if the field value is not _null_.
         */
        @JsStatic
        @JvmStatic
        val IS_NOT_NULL = def(QueryOp::class, "isNotNull")

        /**
         * Tests if the field value is explicitly _true_.
         */
        @JsStatic
        @JvmStatic
        val IS_TRUE = def(QueryOp::class, "isTrue")

        /**
         * Tests if the field value is explicitly _false_.
         */
        @JsStatic
        @JvmStatic
        val IS_FALSE = def(QueryOp::class, "isFalse")

        /**
         * Performs in property inspection, only works on [Property.feature] and [Property.properties]:
         *
         * - If the property is array, then the provided value also has to be an array (`["value"]`).
         * - If the property is object, then the provided value also has to be an object (`{"prop":"value"}`).
         * - If the property is primitive, then the provided value also has to be primitive (`"value"`).
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
         * Also have in mind that provided [PRef] can't contain array properties in the middle of path. Array property is allowed only as last element of path. This is correct: `properties -> reference`, and this is not correct: `properties -> reference -> id`, because `reference` is an array.
         */
        @JsStatic
        @JvmStatic
        val CONTAINS = def(QueryOp::class, "contains")
    }
}
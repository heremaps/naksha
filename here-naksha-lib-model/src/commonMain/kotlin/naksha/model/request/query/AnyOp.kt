@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.PlatformEnum
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * An arbitrary query operation.
 * @since 3.0
 * @see AnyOp
 * @see DoubleOp
 * @see StringOp
 */
@JsExport
open class AnyOp : PlatformEnum() {
    companion object AnyOp_C {
        /**
         * The [PlatformType] of [AnyOp].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(AnyOp::class).withPackageName(PACKAGE_NAME)

        /**
         * Tests if the property exists.
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val EXISTS = def(TYPE, "exists")

        /**
         * Tests if the field value equals to any of the given values, requires the parameter value to be an [Array].
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val IS_ANY_OF = def(TYPE, "anyOf")

        /**
         * Tests if the field value is _null_.
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val IS_NULL = def(TYPE, "isNull")

        /**
         * Tests if the field value is not _null_.
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val IS_NOT_NULL = def(TYPE, "isNotNull")

        /**
         * Tests if the field value is explicitly _true_.
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val IS_TRUE = def(TYPE, "isTrue")

        /**
         * Tests if the field value is explicitly _false_.
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val IS_FALSE = def(TYPE, "isFalse")

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
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val CONTAINS = def(TYPE, "contains")
    }

    override fun namespace() = TYPE
    override fun initClass() {
        register(DoubleOp.TYPE)
        register(StringOp.TYPE)
    }

}
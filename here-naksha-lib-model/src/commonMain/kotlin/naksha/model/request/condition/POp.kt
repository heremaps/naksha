package naksha.model.request.condition

import naksha.base.Int64
import naksha.model.request.condition.POpType.*
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * All property operations.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class POp internal constructor(val op: POpType, val propertyRef: PRef, val value: Any?) : Op {

    override fun getType(): OpType {
        return op
    }

    companion object {

        fun exists(propertyRef: PRef): Op {
            return POp(EXISTS, propertyRef, null)
        }

        fun startsWith(propertyRef: PRef, prefix: String): Op {
            return POp(STARTS_WITH, propertyRef, prefix)
        }

        @JsName("eqString")
        fun eq(propertyRef: PRef, value: String): Op {
            return POp(EQ, propertyRef, value)
        }

        @JsName("eqInt")
        fun eq(propertyRef: PRef, value: Int): Op {
            return POp(EQ, propertyRef, value)
        }

        @JsName("eqInt64")
        fun eq(propertyRef: PRef, value: Int64): Op {
            return POp(EQ, propertyRef, value)
        }

        @JsName("eqDouble")
        fun eq(propertyRef: PRef, value: Double): Op {
            return POp(EQ, propertyRef, value)
        }

        @JsName("eqBoolean")
        fun eq(propertyRef: PRef, value: Boolean): Op {
            return POp(EQ, propertyRef, value)
        }

        @JsName("gtInt")
        fun gt(propertyRef: PRef, value: Int): Op {
            return POp(GT, propertyRef, value)
        }

        @JsName("gtInt64")
        fun gt(propertyRef: PRef, value: Int64): Op {
            return POp(GT, propertyRef, value)
        }

        @JsName("gtDouble")
        fun gt(propertyRef: PRef, value: Double): Op {
            return POp(GT, propertyRef, value)
        }

        @JsName("gteInt")
        fun gte(propertyRef: PRef, value: Int): Op {
            return POp(GTE, propertyRef, value)
        }

        @JsName("gteInt64")
        fun gte(propertyRef: PRef, value: Int64): Op {
            return POp(GTE, propertyRef, value)
        }

        @JsName("gteDouble")
        fun gte(propertyRef: PRef, value: Double): Op {
            return POp(GTE, propertyRef, value)
        }

        @JsName("ltInt")
        fun lt(propertyRef: PRef, value: Int): Op {
            return POp(LT, propertyRef, value)
        }

        @JsName("ltInt64")
        fun lt(propertyRef: PRef, value: Int64): Op {
            return POp(LT, propertyRef, value)
        }

        @JsName("ltDouble")
        fun lt(propertyRef: PRef, value: Double): Op {
            return POp(LT, propertyRef, value)
        }

        @JsName("lteInt")
        fun lte(propertyRef: PRef, value: Int): Op {
            return POp(LTE, propertyRef, value)
        }

        @JsName("lteInt64")
        fun lte(propertyRef: PRef, value: Int64): Op {
            return POp(LTE, propertyRef, value)
        }

        @JsName("lteDouble")
        fun lte(propertyRef: PRef, value: Double): Op {
            return POp(LTE, propertyRef, value)
        }


        fun isNull(propertyRef: PRef): Op {
            return POp(NULL, propertyRef, null)
        }


        fun isNotNull(propertyRef: PRef): Op {
            return POp(NOT_NULL, propertyRef, null)
        }

        /**
         * If your property is array then provided value also has to be an array.
         * <pre>`["value"]`</pre> <br></br>
         * If your property is object then provided value also has to be an object.
         * <pre>`{"prop":"value"}`</pre> <br></br>
         * If your property is primitive value then provided value also has to be primitive.
         * <pre>`"value"`</pre> <br></br>
         * Only top level values search are supported. For json:
         * <pre>`{
         * "type": "Feature",
         * "properties": {
         * "reference": [
         * {
         * "id": "106003684",
         * "prop":{"a":1},
         * }
         * ]
         * }
         * }
        `</pre> *
         * <br></br>
         * You can query path ["properties","reference"] by direct children:
         * <pre>`[{"id":"106003684"}]
         * and
         * [{"prop":{"a":1}}]
        `</pre> *
         * <br></br>
         * But querying by sub property that is not direct child won't work:
         * <pre>`{"a":1} `</pre>
         *
         * Also have in mind that provided [PRef] can't contain array properties in the middle of path.
         * Array property is allowed only as last element of path.
         * This is correct:
         * <pre>`properties -> reference`</pre><br></br>
         *
         * This is not correct:
         * <pre>`properties -> reference -> id`</pre>
         * beacause `reference` is an array
         *
         * @param propertyRef
         * @param value
         * @return
         */

        fun contains(propertyRef: PRef, value: Any?): Op {
            return POp(CONTAINS, propertyRef, value)
        }
    }
}
@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.condition

import naksha.base.Int64
import naksha.model.request.condition.POpType.*
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * All property operations.
 */
@JsExport
class POp internal constructor(val op: POpType, val propertyRef: PRef, val value: Any?) : Op {

    override fun getType(): OpType {
        return op
    }

    companion object POpCompanion {

        @JsStatic
        @JvmStatic
        fun exists(propertyRef: PRef): Op {
            return POp(EXISTS, propertyRef, null)
        }

        @JsStatic
        @JvmStatic
        fun startsWith(propertyRef: PRef, prefix: String): Op {
            return POp(STARTS_WITH, propertyRef, prefix)
        }

        @JsStatic
        @JvmStatic
        @JsName("eqString")
        fun eq(propertyRef: PRef, value: String): Op {
            return POp(EQ, propertyRef, value)
        }

        @JsStatic
        @JvmStatic
        @JsName("eqInt")
        fun eq(propertyRef: PRef, value: Int): Op {
            return POp(EQ, propertyRef, value)
        }

        @JsStatic
        @JvmStatic
        @JsName("eqInt64")
        fun eq(propertyRef: PRef, value: Int64): Op {
            return POp(EQ, propertyRef, value)
        }

        @JsStatic
        @JvmStatic
        @JsName("eqDouble")
        fun eq(propertyRef: PRef, value: Double): Op {
            return POp(EQ, propertyRef, value)
        }

        @JsStatic
        @JvmStatic
        @JsName("eqBoolean")
        fun eq(propertyRef: PRef, value: Boolean): Op {
            return POp(EQ, propertyRef, value)
        }

        @JsStatic
        @JvmStatic
        @JsName("gtInt")
        fun gt(propertyRef: PRef, value: Int): Op {
            return POp(GT, propertyRef, value)
        }

        @JsStatic
        @JvmStatic
        @JsName("anyInt")
        fun any(propertyRef: PRef, value: Array<Int>): Op {
            return POp(ANY, propertyRef, value)
        }

        @JsStatic
        @JvmStatic
        @JsName("anyString")
        fun any(propertyRef: PRef, value: Array<String>): Op {
            return POp(ANY, propertyRef, value)
        }

        @JsStatic
        @JvmStatic
        @JsName("anyInt64")
        fun any(propertyRef: PRef, value: Array<Int64>): Op {
            return POp(ANY, propertyRef, value)
        }

        @JsStatic
        @JvmStatic
        @JsName("gtInt64")
        fun gt(propertyRef: PRef, value: Int64): Op {
            return POp(GT, propertyRef, value)
        }

        @JsStatic
        @JvmStatic
        @JsName("gtDouble")
        fun gt(propertyRef: PRef, value: Double): Op {
            return POp(GT, propertyRef, value)
        }

        @JsStatic
        @JvmStatic
        @JsName("gteInt")
        fun gte(propertyRef: PRef, value: Int): Op {
            return POp(GTE, propertyRef, value)
        }

        @JsStatic
        @JvmStatic
        @JsName("gteInt64")
        fun gte(propertyRef: PRef, value: Int64): Op {
            return POp(GTE, propertyRef, value)
        }

        @JsStatic
        @JvmStatic
        @JsName("gteDouble")
        fun gte(propertyRef: PRef, value: Double): Op {
            return POp(GTE, propertyRef, value)
        }

        @JsStatic
        @JvmStatic
        @JsName("ltInt")
        fun lt(propertyRef: PRef, value: Int): Op {
            return POp(LT, propertyRef, value)
        }

        @JsStatic
        @JvmStatic
        @JsName("ltInt64")
        fun lt(propertyRef: PRef, value: Int64): Op {
            return POp(LT, propertyRef, value)
        }

        @JsStatic
        @JvmStatic
        @JsName("ltDouble")
        fun lt(propertyRef: PRef, value: Double): Op {
            return POp(LT, propertyRef, value)
        }

        @JsStatic
        @JvmStatic
        @JsName("lteInt")
        fun lte(propertyRef: PRef, value: Int): Op {
            return POp(LTE, propertyRef, value)
        }

        @JsStatic
        @JvmStatic
        @JsName("lteInt64")
        fun lte(propertyRef: PRef, value: Int64): Op {
            return POp(LTE, propertyRef, value)
        }

        @JsStatic
        @JvmStatic
        @JsName("lteDouble")
        fun lte(propertyRef: PRef, value: Double): Op {
            return POp(LTE, propertyRef, value)
        }

        @JsStatic
        @JvmStatic
        fun isNull(propertyRef: PRef): Op {
            return POp(NULL, propertyRef, null)
        }

        @JsStatic
        @JvmStatic
        fun isNotNull(propertyRef: PRef): Op {
            return POp(NOT_NULL, propertyRef, null)
        }

        @JsStatic
        @JvmStatic
        fun isIn(propertyRef: PRef, value: Array<String>): Op {
            return POp(IN, propertyRef, value)
        }

        // TODO: Review this, this is hard to impossible to implement in alternative storages (e.g. in SQLLite)!
        /**
         * If your property is array, then the provided value also has to be an array (`["value"]`).
         *
         * If your property is object, then the provided value also has to be an object (`{"prop":"value"}`).
         *
         * If your property is primitive, then the provided value also has to be primitive (`"value"`).
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
         *
         * @param propertyRef
         * @param value
         * @return
         */
        @JsStatic
        @JvmStatic
        fun contains(propertyRef: PRef, value: Any?): POp {
            return POp(CONTAINS, propertyRef, value)
        }
    }
}
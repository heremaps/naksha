@file:OptIn(ExperimentalJsExport::class, ExperimentalJsStatic::class)

package naksha.model.request.ops

import naksha.base.AnyObject
import naksha.base.MapProxy
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import kotlin.js.ExperimentalJsExport
import kotlin.js.ExperimentalJsStatic
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * A member operation.
 * @since 3.0
 */
@JsExport
open class Op : AnyObject() {
    companion object MemberOp_C {
        private val STRING = NotNullProperty<Op, String>(String::class)
        private val STRING_OR_NULL = NullableProperty<Op, String>(String::class)

        const val AND = "and"
        const val OR = "or"
        const val NOT = "not"
        const val IS_NULL = "is_null"
        const val IS_TRUE = "is_true"
        const val IS_FALSE = "is_false"
        const val EQ = "eq"
        const val GT = "gt"
        const val GTE = "gte"
        const val LT = "lt"
        const val LTE = "lte"
        const val STARTS_WITH = "starts_with"
        const val ANY_OF = "any_of"
        const val INTERSECTS = "intersects"
        const val TAG_EXISTS = "tag_exists"
        const val TAG_HAS_ANY_OF = "tag_has_any_of"
        const val TAG_HAS_ALL_OF = "tag_has_all_of"
        const val TAG_IS_NULL = "tag_is_null"
        const val TAG_EQ = "tag_eq"
        const val TAG_STARTS_WITH = "tag_starts_with"
        const val TAG_GT = "tag_gt"
        const val TAG_GTE = "tag_gte"
        const val TAG_LT = "tag_lt"
        const val TAG_LTE = "tag_lte"
        const val TAGLIST_HAS_ANY_OF = "taglist_has_any_of"
        const val TAGLIST_HAS_ALL_OF = "taglist_has_all_of"

        /**
         * Auto-detect the concrete type of member operation and return the cast real type.
         * @param op the member-operation to detect the real type.
         * @return the real [Op] instance or just `null`, if no real type is known.
         */
        @JvmStatic
        @JsStatic
        fun detect(op: MapProxy<*,*>): Op? = when(op.getRaw("op") as String?) {
            AND -> op.proxy(And::class)
            OR -> op.proxy(Or::class)
            NOT -> op.proxy(Not::class)
            IS_NULL -> op.proxy(IsNull::class)
            IS_TRUE -> op.proxy(IsTrue::class)
            IS_FALSE -> op.proxy(IsFalse::class)
            EQ -> op.proxy(Equals::class)
            GT -> op.proxy(Gt::class)
            GTE -> op.proxy(Gte::class)
            LT -> op.proxy(Lt::class)
            LTE -> op.proxy(Lte::class)
            STARTS_WITH -> op.proxy(StartsWith::class)
            ANY_OF -> op.proxy(IsAnyOf::class)
            INTERSECTS -> op.proxy(Intersects::class)
            TAG_EXISTS -> op.proxy(TagExists::class)
            TAG_HAS_ANY_OF -> op.proxy(TagHasAnyOf::class)
            TAG_HAS_ALL_OF -> op.proxy(TagHasAllOf::class)
            TAG_IS_NULL -> op.proxy(TagIsNull::class)
            TAG_EQ -> op.proxy(TagEquals::class)
            TAG_STARTS_WITH -> op.proxy(TagStartsWith::class)
            TAG_GT -> op.proxy(TagGt::class)
            TAG_GTE -> op.proxy(TagGte::class)
            TAG_LT -> op.proxy(TagLt::class)
            TAG_LTE -> op.proxy(TagLte::class)
            TAGLIST_HAS_ANY_OF -> op.proxy(TagListHasAnyOf::class)
            TAGLIST_HAS_ALL_OF -> op.proxy(TagListHasAllOf::class)
            else -> null
        }
    }

    /**
     * The operation identifier.
     */
    var op: String by STRING

    /**
     * The name of the member to query; if any _(some operations do not work upon members)_.
     */
    var at: String? by STRING_OR_NULL
}
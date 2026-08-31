@file:OptIn(ExperimentalJsExport::class, ExperimentalJsStatic::class)

package naksha.model.request.ops

import naksha.base.AnyObject
import naksha.base.MapProxy
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.Platform
import naksha.base.Platform.PlatformCompanion.toJSON
import naksha.base.illegalArg
import naksha.base.proxy
import kotlin.js.ExperimentalJsExport
import kotlin.js.ExperimentalJsStatic
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * A member operation.
 * @since 3.0
 * @see And
 * @see Equals
 * @see Gt
 * @see Gte
 * @see Intersects
 * @see IsAnyOf
 * @see IsFalse
 * @see IsNull
 * @see IsTrue
 * @see Lt
 * @see Lte
 * @see Not
 * @see Or
 * @see StartsWith
 * @see TagEquals
 * @see TagGt
 * @see TagGte
 * @see TagIsNull
 * @see TagListContains
 * @see TagListContainsAllOf
 * @see TagListContainsAnyOf
 * @see TagMapHasAllOf
 * @see TagMapHasAnyOf
 * @see TagMapHasKey
 * @see TagMatches
 * @see TagStartsWith
 */
@JsExport
open class Op : AnyObject() {
    companion object MemberOp_C {
        private val OP_STRING = NotNullProperty<Op, String>(String::class) { self, name ->
            when (self) {
                is And -> AND
                is Or -> OR
                is Not -> NOT
                is IsNull -> IS_NULL
                is IsTrue -> IS_TRUE
                is IsFalse -> IS_FALSE
                is Equals -> EQ
                is Gt -> GT
                is Gte -> GTE
                is Lt -> LT
                is Lte -> LTE
                is StartsWith -> STARTS_WITH
                is IsAnyOf -> IS_ANY_OF
                is Intersects -> INTERSECTS
                is TagMapHasKey -> TAGMAP_HAS_KEY
                is TagMapHasAnyOf -> TAGMAP_HAS_ANY_OF
                is TagMapHasAllOf -> TAGMAP_HAS_ALL_OF
                is TagIsNull -> TAG_IS_NULL
                is TagEquals -> TAG_EQ
                is TagGt -> TAG_GT
                is TagGte -> TAG_GTE
                is TagLt -> TAG_LT
                is TagLte -> TAG_LTE
                is TagStartsWith -> TAG_STARTS_WITH
                is TagMatches -> TAG_MATCHES
                is TagListContains -> TAGLIST_CONTAINS
                is TagListContainsAnyOf -> TAGLIST_CONTAINS_ANY_OF
                is TagListContainsAllOf -> TAGLIST_CONTAINS_ALL_OF
                else -> throw illegalArg("Missing '$name' property or no valid string: ${toJSON(self)}")
            }
        }
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
        const val IS_ANY_OF = "any_of"
        const val INTERSECTS = "intersects"
        const val TAGMAP_HAS_KEY = "tagmap_has_key"
        const val TAGMAP_HAS_ANY_OF = "tagmap_has_any_of"
        const val TAGMAP_HAS_ALL_OF = "tagmap_has_all_of"
        const val TAG_IS_NULL = "tag_is_null"
        const val TAG_EQ = "tag_eq"
        const val TAG_GT = "tag_gt"
        const val TAG_GTE = "tag_gte"
        const val TAG_LT = "tag_lt"
        const val TAG_LTE = "tag_lte"
        const val TAG_STARTS_WITH = "tag_starts_with"
        const val TAG_MATCHES = "tag_matches"
        @Suppress("SpellCheckingInspection")
        const val TAGLIST_CONTAINS = "taglist_contains"
        @Suppress("SpellCheckingInspection")
        const val TAGLIST_CONTAINS_ANY_OF = "taglist_contains_any_of"
        @Suppress("SpellCheckingInspection")
        const val TAGLIST_CONTAINS_ALL_OF = "taglist_contains_all_of"

        /**
         * Auto-detect the concrete type of member operation and return the cast real type.
         * @param op the object to detect the real operation from.
         * @return the real [Op] instance _(for example [Equals], [Gt], ...)_.
         * @since 3.0
         * @throws naksha.base.NakshaException with [ILLEGAL_ARGUMENT][naksha.base.NakshaError.ILLEGAL_ARGUMENT] error, when the given operation, or it's name (`op` property) is invalid/unknown.
         */
        @JvmStatic
        @JsStatic
        fun detect(op: Any?): Op {
            val realOp = op as? Op? ?: op.proxy(Op::class)
                ?: throw illegalArg("The given operation is not detectable: ${toJSON(op)}")
            // If op is already a concrete type.
            if (realOp::class !== Op::class) return realOp
            val opName = realOp.op
            val opProxy = when(opName) {
                AND -> op as? And ?: op.proxy(And::class)
                OR -> op as? Or ?: op.proxy(Or::class)
                NOT -> op as? Not ?: op.proxy(Not::class)
                IS_NULL -> op as? IsNull ?: op.proxy(IsNull::class)
                IS_TRUE -> op as? IsTrue ?: op.proxy(IsTrue::class)
                IS_FALSE -> op as? IsFalse ?: op.proxy(IsFalse::class)
                EQ -> op as? Equals ?: op.proxy(Equals::class)
                GT -> op as? Gt ?: op.proxy(Gt::class)
                GTE -> op as? Gte ?: op.proxy(Gte::class)
                LT -> op as? Lt ?: op.proxy(Lt::class)
                LTE -> op as? Lte ?: op.proxy(Lte::class)
                STARTS_WITH -> op as? StartsWith ?: op.proxy(StartsWith::class)
                IS_ANY_OF -> op as? IsAnyOf ?: op.proxy(IsAnyOf::class)
                INTERSECTS -> op as? Intersects ?: op.proxy(Intersects::class)
                TAGMAP_HAS_KEY -> op as? TagMapHasKey ?: op.proxy(TagMapHasKey::class)
                TAGMAP_HAS_ANY_OF -> op as? TagMapHasAnyOf ?: op.proxy(TagMapHasAnyOf::class)
                TAGMAP_HAS_ALL_OF -> op as? TagMapHasAllOf ?: op.proxy(TagMapHasAllOf::class)
                TAG_IS_NULL -> op as? TagIsNull ?: op.proxy(TagIsNull::class)
                TAG_EQ -> op as? TagEquals ?: op.proxy(TagEquals::class)
                TAG_STARTS_WITH -> op as? TagStartsWith ?: op.proxy(TagStartsWith::class)
                TAG_MATCHES -> op as? TagMatches ?: op.proxy(TagMatches::class)
                TAG_GT -> op as? TagGt ?: op.proxy(TagGt::class)
                TAG_GTE -> op as? TagGte ?: op.proxy(TagGte::class)
                TAG_LT -> op as? TagLt ?: op.proxy(TagLt::class)
                TAG_LTE -> op as? TagLte ?: op.proxy(TagLte::class)
                TAGLIST_CONTAINS -> op as? TagListContains ?: op.proxy(TagListContains::class)
                TAGLIST_CONTAINS_ANY_OF -> op as? TagListContainsAnyOf ?: op.proxy(TagListContainsAnyOf::class)
                TAGLIST_CONTAINS_ALL_OF -> op as? TagListContainsAllOf ?: op.proxy(TagListContainsAllOf::class)
                else -> null
            }
            return opProxy ?: throw illegalArg("Unknown operation: $opName")
        }
   }

    /**
     * The operation identifier.
     *
     * Throws [NakshaException][naksha.base.NakshaException] with error [ILLEGAL_ARGUMENT][naksha.base.NakshaError.ILLEGAL_ARGUMENT] if the `op` property is no valid string. Does not verify if the name is a valid and supported one, only ensures that the `op` property is a string.
     */
    var op: String by OP_STRING

    /**
     * The name of the member to query; if any _(some operations do not work upon members)_.
     */
    var at: String? by STRING_OR_NULL
}

@file:Suppress("OPT_IN_USAGE")

package naksha.auth.check

import naksha.base.*
import naksha.base.Platform.Platform_C.forKClass
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The check operation-code.
 *
 * Services and applications can extend this enumeration to introduce own custom checks with own op-codes.
 * @since 3.0
 */
@JsExport
open class CheckOp : JsEnum() {
    companion object CheckOp_C {
        /**
         * The [PlatformType] of [CheckOp].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<CheckOp> = forKClass(CheckOp::class).withPackageName(PACKAGE_NAME)

        /**
         * Returns the [CheckOp] for the given string.
         * @param op The op-code as string.
         * @return the [CheckOp] value or [UNDEFINED], if an no op-code was given _(null)_.
         */
        fun of(op: Any?): CheckOp = if (op == null) UNDEFINED else get(op, TYPE)

        /**
         * The `undefined` value.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val UNDEFINED = defIgnoreCase(TYPE, "undefined") { self ->
            self.checkType = Check.TYPE
        }

        /**
         * The [Equals].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val EQUALS = defIgnoreCase(TYPE, "eq") { self ->
            self.checkType = Equals.TYPE
        }.alias<CheckOp>("equals").alias<CheckOp>("equal")

        /**
         * The [StartsWith].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val STARTS_WITH = defIgnoreCase(TYPE, "startsWith") { self ->
            self.checkType = StartsWith.TYPE
        }

        /**
         * The [EndsWith].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val ENDS_WITH = defIgnoreCase(TYPE, "endsWith") { self ->
            self.checkType = EndsWith.TYPE
        }

        /**
         * The [Contains].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val CONTAINS = defIgnoreCase(TYPE, "contains") { self ->
            self.checkType = Contains.TYPE
        }

        /**
         * The [ContainsKey].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val CONTAINS_KEY = defIgnoreCase(TYPE, "containsKey") { self ->
            self.checkType = ContainsKey.TYPE
        }

        /**
         * The [ContainsValue].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val CONTAINS_VALUE = defIgnoreCase(TYPE, "containsValue") { self ->
            self.checkType = ContainsValue.TYPE
        }

        /**
         * The [MatchesKey].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val MATCHES_KEY = defIgnoreCase(TYPE, "matchesKey") { self ->
            self.checkType = MatchesKey.TYPE
        }

        /**
         * The [MatchesValue].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val MATCHES_VALUE = defIgnoreCase(TYPE, "matchesValue") { self ->
            self.checkType = MatchesValue.TYPE
        }
    }

    /**
     * The [PlatformType] of the check implementation.
     * @since 3.0
     */
    var checkType: PlatformType<out Check> = Check.TYPE
        protected set

    override fun namespace(): PlatformType<out JsEnum> = TYPE
    override fun initClass() {}
}
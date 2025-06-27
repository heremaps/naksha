package naksha.model

import naksha.base.PlatformEnum
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import naksha.model.FlagsBits.FlagsBits_C.ACTION_SHIFT
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * An enumeration about the action that actually was performed for a feature in a storage, being [CREATE], [UPDATE], or [DELETE].
 *
 * @since 1.0.0
 * @see [Operation]
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class Action : PlatformEnum() {
    override fun namespace(): PlatformType<out PlatformEnum> = TYPE

    override fun initClass() {}

    @Suppress("MemberVisibilityCanBePrivate")
    companion object Action_C {
        /**
         * The [PlatformType] of [Action].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(Action::class).withPackageName(PACKAGE_NAME)

        internal const val CREATE_VALUE = 0 shl ACTION_SHIFT
        internal const val CREATE_STRING = "CREATE"
        internal const val CREATED_STRING = "CREATED"
        internal const val CREATE_SHORT = "c"

        internal const val UPDATE_VALUE = 1 shl ACTION_SHIFT
        internal const val UPDATE_STRING = "UPDATE"
        internal const val UPDATED_STRING = "UPDATED"
        internal const val UPDATE_SHORT = "u"

        internal const val DELETE_VALUE = 2 shl ACTION_SHIFT
        internal const val DELETE_STRING = "DELETE"
        internal const val DELETED_STRING = "DELETED"
        internal const val DELETE_SHORT = "d"

        internal const val UNDEFINED_VALUE = 3 shl ACTION_SHIFT
        internal const val UNDEFINED_STRING = "UNDEFINED"
        internal const val UNDEFINED_SHORT = "x"

        /**
         * The feature was created.
         * @since 1.0.0
         */
        @JsStatic
        @JvmField
        val CREATE = defIgnoreCase(TYPE, CREATE_STRING) { self ->
            self.intValue = CREATE_VALUE
            self.shortId = CREATE_SHORT
        }.alias<Action>(CREATED_STRING)

        @Deprecated(
            message = "Deprecated, please use CREATE",
            replaceWith = ReplaceWith("CREATE"),
            level = DeprecationLevel.ERROR
        )
        @JsStatic
        @JvmField
        val CREATED = CREATE

        /**
         * The feature was updated.
         * @since 1.0.0
         */
        @JsStatic
        @JvmField
        val UPDATE = defIgnoreCase(TYPE, UPDATE_STRING) { self ->
            self.intValue = UPDATE_VALUE
            self.shortId = UPDATE_SHORT
        }.alias<Action>(UPDATED_STRING)

        @Deprecated(
            message = "Deprecated, please use UPDATE",
            replaceWith = ReplaceWith("UPDATE"),
            level = DeprecationLevel.ERROR
        )
        @JsStatic
        @JvmField
        val UPDATED = UPDATE

        /**
         * The feature was deleted.
         * @since 1.0.0
         */
        @JsStatic
        @JvmField
        val DELETE = defIgnoreCase(TYPE, DELETE_STRING) { self ->
            self.intValue = DELETE_VALUE
            self.shortId = DELETE_SHORT
        }.alias<Action>(DELETED_STRING)

        @Deprecated(
            message = "Deprecated, please use DELETE",
            replaceWith = ReplaceWith("DELETE"),
            level = DeprecationLevel.ERROR
        )
        @JsStatic
        @JvmField
        val DELETED = DELETE

        /**
         * The action is unknown (invalid state).
         * @since 1.0.0
         */
        @JsStatic
        @JvmField
        val UNDEFINED = defIgnoreCase(TYPE, UNDEFINED_STRING) { self ->
            self.intValue = UNDEFINED_VALUE
            self.shortId = UNDEFINED_SHORT
        }

        // This supports full-qualified names (that default JsEnum support as well) PLUS short notation!
        private val FROM_STRING = mapOf(
            Pair(CREATE_STRING, CREATE), Pair(CREATE_SHORT, CREATE), Pair(CREATED_STRING, CREATE),
            Pair(UPDATE_STRING, UPDATE), Pair(UPDATE_SHORT, UPDATE), Pair(UPDATED_STRING, UPDATE),
            Pair(DELETE_STRING, DELETE), Pair(DELETE_SHORT, DELETE), Pair(DELETED_STRING, DELETE),
        )

        private val FROM_VALUE = mapOf(
            Pair(CREATE_VALUE, CREATE),
            Pair(UPDATE_VALUE, UPDATE),
            Pair(DELETE_VALUE, DELETE),
        )

        /**
         * Helper to parse a string into an [Action].
         */
        @JsStatic
        @JvmStatic
        fun fromString(s: String): Action = FROM_STRING[s] ?: UNDEFINED

        /**
         * Helper to parse a string into an [Action].
         */
        @JsStatic
        @JvmStatic
        fun fromValue(value: Int): Action = FROM_VALUE[value] ?: UNDEFINED
    }

    /**
     * The action value.
     * @since 1.0.0
     * @see [fromValue]
     */
    var intValue: Int = UNDEFINED_VALUE
        private set

    /**
     * The short identifier, if there is any.
     * @since 1.0.0
     */
    var shortId: String = UNDEFINED_STRING
        private set
}
package naksha.model

import naksha.base.JsEnum
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass

/**
 * An enumeration about the action that actually was performed for a feature in a storage, being [CREATED], [UPDATED], or [DELETED].
 *
 * The numeric [intValue] corresponds to the lower two bits of a [Version.txn].
 *
 * @since 1.0.0
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class Action : JsEnum() {
    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = Action::class

    override fun initClass() {}

    @Suppress("MemberVisibilityCanBePrivate")
    companion object Action_C {
        internal const val CREATED_VALUE = 0
        internal const val CREATED_STRING = "CREATE"
        internal const val CREATED_SHORT = "c"

        internal const val UPDATED_VALUE = 1
        internal const val UPDATED_STRING = "UPDATE"
        internal const val UPDATED_SHORT = "u"

        internal const val DELETED_VALUE = 2
        internal const val DELETED_STRING = "DELETE"
        internal const val DELETED_SHORT = "d"

        internal const val UNDEFINED_VALUE = 3
        internal const val UNDEFINED_STRING = "UNDEFINED"
        internal const val UNDEFINED_SHORT = "x"

        /**
         * The feature was created.
         * @since 1.0.0
         */
        @JsStatic
        @JvmField
        val CREATED = defIgnoreCase(Action::class, CREATED_STRING) { self ->
            self.intValue = CREATED_VALUE
            self.shortId = CREATED_SHORT
        }

        /**
         * The feature was updated.
         * @since 1.0.0
         */
        @JsStatic
        @JvmField
        val UPDATED = defIgnoreCase(Action::class, UPDATED_STRING) { self ->
            self.intValue = UPDATED_VALUE
            self.shortId = UPDATED_SHORT
        }

        /**
         * The feature was deleted.
         * @since 1.0.0
         */
        @JsStatic
        @JvmField
        val DELETED = defIgnoreCase(Action::class, DELETED_STRING) { self ->
            self.intValue = DELETED_VALUE
            self.shortId = DELETED_SHORT
        }

        /**
         * The action is unknown (invalid state).
         * @since 1.0.0
         */
        @JsStatic
        @JvmField
        val UNDEFINED = defIgnoreCase(Action::class, UNDEFINED_STRING) { self ->
            self.intValue = UNDEFINED_VALUE
            self.shortId = UNDEFINED_SHORT
        }

        // This supports full-qualified names (that default JsEnum support as well) PLUS short notation!
        private val FROM_STRING = mapOf(
            Pair(CREATED_STRING, CREATED), Pair(CREATED_SHORT, CREATED),
            Pair(UPDATED_STRING, UPDATED), Pair(UPDATED_SHORT, UPDATED),
            Pair(DELETED_STRING, DELETED), Pair(DELETED_SHORT, DELETED),
        )

        private val FROM_VALUE = mapOf(
            Pair(CREATED_VALUE, CREATED),
            Pair(UPDATED_VALUE, UPDATED),
            Pair(DELETED_VALUE, DELETED),
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
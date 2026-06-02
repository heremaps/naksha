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
 * The numeric [intValue] corresponds to the lower two bits of a [Version.txn]:
 * - `0` ([CREATED]) — the feature was created in this version.
 * - `1` ([UPDATED]) — the feature was updated in this version.
 * - `2` ([DELETED]) — the feature was deleted in this version.
 * - `3` ([VERSION]) — both action bits are set; used as a sentinel to indicate that the [Version.txn] value itself
 *   is being used as a version reference rather than encoding a state-change action.
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

        internal const val VERSION_VALUE = 3
        internal const val VERSION_STRING = "VERSION"
        internal const val VERSION_SHORT = "v"

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
         * Both action bits are set (`3`). Used as a sentinel to signal that the [Version.txn] value
         * is a version reference rather than a state-change action. Also returned by [fromValue] for
         * any unrecognised integer value.
         * @since 1.0.0
         */
        @JsStatic
        @JvmField
        val VERSION = defIgnoreCase(Action::class, VERSION_STRING) { self ->
            self.intValue = VERSION_VALUE
            self.shortId = VERSION_SHORT
        }

        // Full-name and short-name lookup map.
        private val FROM_STRING = mapOf(
            Pair(CREATED_STRING, CREATED), Pair(CREATED_SHORT, CREATED),
            Pair(UPDATED_STRING, UPDATED), Pair(UPDATED_SHORT, UPDATED),
            Pair(DELETED_STRING, DELETED), Pair(DELETED_SHORT, DELETED),
            Pair(VERSION_STRING, VERSION), Pair(VERSION_SHORT, VERSION),
        )

        private val FROM_VALUE = mapOf(
            Pair(CREATED_VALUE, CREATED),
            Pair(UPDATED_VALUE, UPDATED),
            Pair(DELETED_VALUE, DELETED),
            Pair(VERSION_VALUE, VERSION),
        )

        /**
         * Helper to parse a string into an [Action]. Returns [VERSION] for unrecognised strings.
         */
        @JsStatic
        @JvmStatic
        fun fromString(s: String): Action = FROM_STRING[s] ?: VERSION

        /**
         * Helper to obtain an [Action] from its integer value. Returns [VERSION] for unrecognised values.
         */
        @JsStatic
        @JvmStatic
        fun fromValue(value: Int): Action = FROM_VALUE[value] ?: VERSION
    }

    /**
     * The action value.
     * @since 1.0.0
     * @see [fromValue]
     */
    var intValue: Int = VERSION_VALUE
        private set

    /**
     * The short identifier.
     * @since 1.0.0
     */
    var shortId: String = VERSION_SHORT
        private set
}

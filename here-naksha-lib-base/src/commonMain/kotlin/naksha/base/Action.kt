package naksha.base

import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass

/**
 * An enumeration about the action that actually was performed for a feature in a storage, being [CREATE], [UPDATE], or [DELETE].
 *
 * The numeric [intValue] corresponds to the lower two bits of a [Version.number]:
 * - `0` ([CREATE]) — the feature was created in this version.
 * - `1` ([UPDATE]) — the feature was updated in this version.
 * - `2` ([DELETE]) — the feature was deleted in this version.
 * - `3` ([VERSION]) — both action bits are set; used as a sentinel to indicate that the [Version.number] value itself
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
        internal const val CREATE_VALUE = 0
        internal const val CREATE_STRING = "CREATE"
        internal const val CREATE_SHORT = "c"

        internal const val UPDATE_VALUE = 1
        internal const val UPDATE_STRING = "UPDATE"
        internal const val UPDATE_SHORT = "u"

        internal const val DELETE_VALUE = 2
        internal const val DELETE_STRING = "DELETE"
        internal const val DELETE_SHORT = "d"

        internal const val VERSION_VALUE = 3
        internal const val VERSION_STRING = "VERSION"
        internal const val VERSION_SHORT = "v"

        /**
         * The feature was created.
         * @since 1.0.0
         */
        @JsStatic
        @JvmField
        val CREATE = defIgnoreCase(Action::class, CREATE_STRING) { self ->
            self.intValue = CREATE_VALUE
            self.longValue = CREATE_VALUE.toLong()
            self.shortId = CREATE_SHORT
        }

        /**
         * The feature was updated.
         * @since 1.0.0
         */
        @JsStatic
        @JvmField
        val UPDATE = defIgnoreCase(Action::class, UPDATE_STRING) { self ->
            self.intValue = UPDATE_VALUE
            self.longValue = UPDATE_VALUE.toLong()
            self.shortId = UPDATE_SHORT
        }

        /**
         * The feature was deleted.
         * @since 1.0.0
         */
        @JsStatic
        @JvmField
        val DELETE = defIgnoreCase(Action::class, DELETE_STRING) { self ->
            self.intValue = DELETE_VALUE
            self.longValue = DELETE_VALUE.toLong()
            self.shortId = DELETE_SHORT
        }

        /**
         * Both action bits are set (`3`). Used as a sentinel to signal that the [Version.number] value
         * is a version reference rather than a state-change action. Also returned by [fromValue] for
         * any unrecognised integer value.
         * @since 1.0.0
         */
        @JsStatic
        @JvmField
        val VERSION = defIgnoreCase(Action::class, VERSION_STRING) { self ->
            self.intValue = VERSION_VALUE
            self.longValue = VERSION_VALUE.toLong()
            self.shortId = VERSION_SHORT
        }

        // Full-name and short-name lookup map.
        private val FROM_STRING = mapOf(
            Pair(CREATE_STRING, CREATE), Pair(CREATE_SHORT, CREATE),
            Pair(UPDATE_STRING, UPDATE), Pair(UPDATE_SHORT, UPDATE),
            Pair(DELETE_STRING, DELETE), Pair(DELETE_SHORT, DELETE),
            Pair(VERSION_STRING, VERSION), Pair(VERSION_SHORT, VERSION),
        )

        private val FROM_VALUE = mapOf(
            Pair(CREATE_VALUE, CREATE),
            Pair(UPDATE_VALUE, UPDATE),
            Pair(DELETE_VALUE, DELETE),
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

        /**
         * Helper to obtain an [Action] from its 64-bit version value. Returns [VERSION] for unrecognised values.
         */
        @JsStatic
        @JvmStatic
        fun fromVersion(version: Long): Action = FROM_VALUE[version.toInt() and 3] ?: VERSION
    }

    /**
     * The action value.
     * @since 1.0.0
     * @see [fromValue]
     */
    var intValue: Int = VERSION_VALUE
        private set

    /**
     * The action value.
     * @since 1.0.0
     * @see [fromValue]
     */
    var longValue: Long = VERSION_VALUE.toLong()
        private set

    /**
     * The short identifier.
     * @since 1.0.0
     */
    var shortId: String = VERSION_SHORT
        private set
}

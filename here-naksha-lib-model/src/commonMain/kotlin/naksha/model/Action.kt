package naksha.model

import naksha.base.JsEnum
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass

/**
 * An enumeration about the supported actions, being [CREATED], [UPDATED] or [DELETED].
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class Action : JsEnum() {
    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = Action::class

    override fun initClass() {}

    companion object ActionEnumCompanion {
        /**
         * The feature was created.
         */
        @JsStatic
        @JvmField
        val CREATED = defIgnoreCase(Action::class, "CREATED") { self ->
            self.action = ActionValues.CREATED
            self.shortId = "c"
        }

        /**
         * The feature was updated.
         */
        @JsStatic
        @JvmField
        val UPDATED = defIgnoreCase(Action::class, "UPDATED") { self ->
            self.action = ActionValues.UPDATED
            self.shortId = "u"
        }

        /**
         * The feature was deleted.
         */
        @JsStatic
        @JvmField
        val DELETED = defIgnoreCase(Action::class, "DELETED") { self ->
            self.action = ActionValues.DELETED
            self.shortId = "d"
        }

        /**
         * The action is unknown (invalid state).
         */
        @JsStatic
        @JvmField
        val UNKNOWN = defIgnoreCase(Action::class, "UNKNOWN") { self -> self.action = ActionValues.UNKNOWN }

        /**
         * Helper to parse a string into an [Action].
         */
        @JsStatic
        @JvmStatic
        fun fromString(s: String): Action = when (s) {
            "CREATED", "c" -> CREATED
            "UPDATED", "u" -> UPDATED
            "DELETED", "d" -> DELETED
            else -> UNKNOWN
        }
    }

    /**
     * The action value.
     */
    var action: Int = ActionValues.UNKNOWN
        private set

    /**
     * The short identifier, if there is any.
     */
    var shortId: String? = null
        private set
}
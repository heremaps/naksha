package naksha.model

import naksha.base.JsEnum
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.reflect.KClass

/**
 * An enumeration about the supported actions.
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
        val CREATED = defIgnoreCase(Action::class, "CREATED") { self -> self.action = ActionValues.CREATED }

        /**
         * The feature was updated.
         */
        @JsStatic
        @JvmField
        val UPDATED = defIgnoreCase(Action::class, "UPDATED") { self -> self.action = ActionValues.UPDATED }

        /**
         * The feature was deleted.
         */
        @JsStatic
        @JvmField
        val DELETED = defIgnoreCase(Action::class, "DELETED") { self -> self.action = ActionValues.DELETED }

        /**
         * The action is unknown (invalid state).
         */
        @JsStatic
        @JvmField
        val UNKNOWN = defIgnoreCase(Action::class, "UNKNOWN") { self -> self.action = ActionValues.UNKNOWN }
    }

    /**
     * The action value.
     */
    var action: Int = ActionValues.UNKNOWN
        private set
}
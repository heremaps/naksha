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
class ActionEnum : JsEnum() {
    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = ActionEnum::class

    override fun initClass() {}

    companion object ActionEnumCompanion {
        /**
         * The feature was created.
         */
        @JsStatic
        @JvmField
        val CREATED = defIgnoreCase(ActionEnum::class, "CREATED") { self -> self.action = Action.CREATED }

        /**
         * The feature was updated.
         */
        @JsStatic
        @JvmField
        val UPDATED = defIgnoreCase(ActionEnum::class, "UPDATED") { self -> self.action = Action.UPDATED }

        /**
         * The feature was deleted.
         */
        @JsStatic
        @JvmField
        val DELETED = defIgnoreCase(ActionEnum::class, "DELETED") { self -> self.action = Action.DELETED }

        /**
         * The action is unknown (invalid state).
         */
        @JsStatic
        @JvmField
        val UNKNOWN = defIgnoreCase(ActionEnum::class, "UNKNOWN") { self -> self.action = Action.UNKNOWN }
    }

    /**
     * The action value.
     */
    var action: Int = Action.UNKNOWN
        private set
}
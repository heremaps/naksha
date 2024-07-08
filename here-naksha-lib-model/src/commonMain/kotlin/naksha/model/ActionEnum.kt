package naksha.model

import naksha.base.JsEnum
import kotlin.js.JsExport
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

    companion object {
        /**
         * The feature was created.
         */
        val CREATED = defIgnoreCase(ActionEnum::class, "CREATED") { self -> self.action = Action.CREATED }

        /**
         * The feature was updated.
         */
        val UPDATED = defIgnoreCase(ActionEnum::class, "UPDATED") { self -> self.action = Action.UPDATED }

        /**
         * The feature was deleted.
         */
        val DELETED = defIgnoreCase(ActionEnum::class, "DELETED") { self -> self.action = Action.DELETED }

        /**
         * The action is unknown (invalid state).
         */
        val UNKNOWN = defIgnoreCase(ActionEnum::class, "UNKNOWN") { self -> self.action = Action.UNKNOWN }
    }

    /**
     * The action value.
     */
    var action: Int = Action.UNKNOWN
        private set
}
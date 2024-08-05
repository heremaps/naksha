package naksha.model.mom

import naksha.base.JsEnum
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.reflect.KClass

/**
 * The change-state enumeration.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class MomChangeState : JsEnum() {
    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = MomChangeState::class

    override fun initClass() {}

    companion object ChangeStateEnumCompanion {

        /**
         * The feature was created (did not exist in base layer).
         */
        @JvmField
        @JsStatic
        val CREATED = defIgnoreCase(MomChangeState::class, "CREATED")

        /**
         * The feature was updated (did exist in base layer).
         */
        @JvmField
        @JsStatic
        val UPDATED = defIgnoreCase(MomChangeState::class, "UPDATED")

        /**
         * The feature was removed from the map.
         */
        @JvmField
        @JsStatic
        val REMOVED = defIgnoreCase(MomChangeState::class, "REMOVED")

        /**
         * The feature was a road or topology and split, which means, it was deleted, but replaced with new child nodes that should be in
         * {@code CREATED} state.
         */
        @JvmField
        @JsStatic
        val SPLIT = defIgnoreCase(MomChangeState::class, "SPLIT")

        fun of(value: String): MomChangeState = get(value, MomChangeState::class)
    }
}
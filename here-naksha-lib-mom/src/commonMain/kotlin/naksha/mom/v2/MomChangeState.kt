package naksha.mom.v2

import naksha.base.JsEnum
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The change-state enumeration.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class MomChangeState : JsEnum() {
    override fun namespace() = TYPE
    override fun initClass() {}

    companion object MomChangeState_C {
        /**
         * The [PlatformType] of [MomChangeState].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(MomChangeState::class).withPackageName(PACKAGE_NAME)

        /**
         * The feature was created (did not exist in base layer).
         */
        @JvmField
        @JsStatic
        val CREATED = defIgnoreCase(TYPE, "CREATED")

        /**
         * The feature was updated (did exist in base layer).
         */
        @JvmField
        @JsStatic
        val UPDATED = defIgnoreCase(TYPE, "UPDATED")

        /**
         * The feature was removed from the map.
         */
        @JvmField
        @JsStatic
        val REMOVED = defIgnoreCase(TYPE, "REMOVED")

        /**
         * The feature was a road or topology and split, which means, it was deleted, but replaced with new child nodes that should be in
         * {@code CREATED} state.
         */
        @JvmField
        @JsStatic
        val SPLIT = defIgnoreCase(TYPE, "SPLIT")

        fun of(value: String): MomChangeState = get(value, TYPE)
    }
}
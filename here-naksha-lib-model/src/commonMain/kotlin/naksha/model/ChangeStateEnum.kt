package naksha.model

import naksha.base.JsEnum
import kotlin.js.JsExport
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass

/**
 * The change-state enumeration.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class ChangeStateEnum : JsEnum() {
    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = ChangeStateEnum::class

    override fun initClass() {}

    companion object ChangeStateEnumCompanion {

        /**
         * The feature was created (did not exist in base layer).
         */
        @JvmField
        val CREATED = defIgnoreCase(ChangeStateEnum::class, "CREATED")

        /**
         * The feature was updated (did exist in base layer).
         */
        @JvmField
        val UPDATED = defIgnoreCase(ChangeStateEnum::class, "UPDATED")

        /**
         * The feature was removed from the map.
         */
        @JvmField
        val REMOVED = defIgnoreCase(ChangeStateEnum::class, "REMOVED")

        /**
         * The feature was a road or topology and split, which means, it was deleted, but replaced with new child nodes that should be in
         * {@code CREATED} state.
         */
        @JvmField
        val SPLIT = defIgnoreCase(ChangeStateEnum::class, "SPLIT")

        fun of(value: String): ChangeStateEnum =
            when (value) {
                CREATED.value -> CREATED
                UPDATED.value -> UPDATED
                REMOVED.value -> REMOVED
                SPLIT.value -> SPLIT
                else -> throw IllegalArgumentException(value)
            }
    }
}
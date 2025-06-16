@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.AtomicInt
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import naksha.model.FlagsBits.FlagsBits_C.ACTION_SHIFT
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A `uid` manager to ensure that correct `uid` values are generated.
 * @since 3.0
 */
@JsExport
class UidManager {
    companion object UidManager_C {
        /**
         * The [PlatformType] of [UidManager].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(UidManager::class).withPackageName(PACKAGE_NAME)
    }

    private val value = AtomicInt(-4)

    /**
     * Returns the last `uid` value generated.
     * @return the last `uid` value generated; `null` if no `uid` was yet generated.
     */
    fun last(): Int? {
        val current = value.get()
        return if (current >= 0) current else null
    }

    /**
     * Returns the next `uid` value for the given action.
     * @param action the action for which to return the next `uid`.
     * @return the next `uid`.
     * @since 3.0
     */
    fun next(action: Action): Int = value.addAndGet(4 + (action.intValue shr ACTION_SHIFT))

    /**
     * Resets the `uid` to start.
     * @return this.
     * @since 3.0
     */
    fun reset(): UidManager {
        value.set(-4)
        return this
    }
}
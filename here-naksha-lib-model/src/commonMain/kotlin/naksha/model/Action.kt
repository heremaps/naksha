package naksha.model

import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * Helper for action encoding in [Flags].
 */
@Suppress("OPT_IN_USAGE")
@JsExport
object Action : FlagsBits() {
    /**
     * Create action.
     */
    const val CREATE = 0 shl ACTION_SHIFT

    /**
     * Update action.
     */
    const val UPDATE = 1 shl ACTION_SHIFT

    /**
     * Delete action.
     */
    const val DELETE = 2 shl ACTION_SHIFT
}
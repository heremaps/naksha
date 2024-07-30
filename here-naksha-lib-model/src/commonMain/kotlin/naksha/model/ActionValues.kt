package naksha.model

import kotlin.js.JsExport

/**
 * Helper for action encoding in [Flags].
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class ActionValues : FlagsBits() {
    companion object ActionCompanion {
        /**
         * Create action.
         */
        const val CREATED = 0 shl ACTION_SHIFT

        /**
         * Update action.
         */
        const val UPDATED = 1 shl ACTION_SHIFT

        /**
         * Delete action.
         */
        const val DELETED = 2 shl ACTION_SHIFT

        /**
         * Unknown action.
         */
        const val UNKNOWN = 3 shl ACTION_SHIFT
    }
}
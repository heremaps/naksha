@file:Suppress("OPT_IN_USAGE")

package naksha.model

import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * Optional options when acquiring a new session.
 * @property map the map to operate on, by default taken from [context][NakshaContext.map].
 * @property parallel allow optimiser to execute requests in parallel, as long as it can provide similar guarantees that a single, not parallel session, would grant. Often this is not possible for writing, but for reading, where failures can be bypassed by simply repeating the operation or falling back to a single connection.
 * @property useMaster only use the master node to avoid replication lag.
 */
@JsExport
data class NakshaSessionOptions(
    @JvmField
    val map: String = NakshaContext.currentContext().map,
    @JvmField
    val parallel: Boolean = false,
    @JvmField
    val useMaster: Boolean = false
)
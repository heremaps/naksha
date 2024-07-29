@file:Suppress("OPT_IN_USAGE")

package naksha.model

import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * The Local Unique Identifier, being a 96-bit value, persisting out of the transaction number ([Version]), and a 32-bit integer that uniquely identifies the state within the transaction. The `uid` allows to order operations within a transaction, and to process the changes of a transaction in a deterministic step-by-step way. The `LUID` is stringified into: `{year}:{month}:{day}:{seq}:{uid}`
 */
@JsExport
data class Luid(@JvmField val version: Version, @JvmField val uid: Int) {
    private lateinit var _string: String

    /**
     * Return the LUID in URN form.
     * @return `{year}:{month}:{day}:{seq}:{uid}`
     */
    override fun toString(): String {
        if (!this::_string.isInitialized) _string = "$version:$uid"
        return _string
    }
}
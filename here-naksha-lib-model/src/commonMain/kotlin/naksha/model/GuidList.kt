@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.ListProxy
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmStatic

/**
 * A list of [Guid]'s.
 *
 * **Warning**: A [Guid] is not serializable, and it is not possible to create it without parameters.
 */
@JsExport
class GuidList : ListProxy<Guid>(Guid::class){

    companion object {
        @JvmStatic
        @JsName("fromGuids")
        fun of(vararg guids: Guid): GuidList {
            return GuidList().apply {
                addAll(guids)
            }
        }
    }
}

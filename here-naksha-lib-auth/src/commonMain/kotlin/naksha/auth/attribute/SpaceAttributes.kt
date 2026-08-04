package naksha.auth.attribute

import naksha.base.PTypedArray
import kotlin.js.JsExport


@JsExport
class SpaceAttributes : NakshaAttributes<StorageAttributes>() {

    fun eventHandlerIds(eventHandlerIds: List<String>) =
        apply {
            box(eventHandlerIds, PTypedArray::class)?.let { set(EVENT_HANDLER_IDS_KEY, it) }
        }

    companion object {
        const val EVENT_HANDLER_IDS_KEY = "eventHandlerIds"
    }
}
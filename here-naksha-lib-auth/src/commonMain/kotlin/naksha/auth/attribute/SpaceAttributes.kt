package naksha.auth.attribute

import naksha.base.AbstractListProxy
import kotlin.js.JsExport


@JsExport
class SpaceAttributes : NakshaAttributes<StorageAttributes>() {

    fun eventHandlerIds(eventHandlerIds: List<String>) =
        apply {
            box(eventHandlerIds, AbstractListProxy::class)?.let { set(EVENT_HANDLER_IDS_KEY, it) }
        }

    companion object {
        const val EVENT_HANDLER_IDS_KEY = "eventHandlerIds"
    }
}
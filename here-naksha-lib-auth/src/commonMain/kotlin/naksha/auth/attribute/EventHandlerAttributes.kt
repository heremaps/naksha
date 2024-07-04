package naksha.auth.attribute

import kotlin.js.JsExport

@JsExport
class EventHandlerAttributes: NakshaAttributes<EventHandlerAttributes>() {

    fun className(className: String) = apply { set(CLASS_NAME_KEY, className) }

    companion object {
        const val CLASS_NAME_KEY = "className"
    }
}
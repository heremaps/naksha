package com.here.naksha.lib.auth.attribute

class EventHandlerAttributes: ResourceAttributes() {

    fun className(className: String) = apply { set(CLASS_NAME_KEY, className) }

    companion object {
        const val CLASS_NAME_KEY = "className"
    }
}
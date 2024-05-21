package com.here.naksha.lib.auth.attribute

import com.here.naksha.lib.auth.AccessAttributeMap

class EventHandlerAttributes(vararg args: Any): AccessAttributeMap(*args) {

    fun className(className: String) = apply { set(CLASS_NAME_KEY, className) }

    companion object {
        const val CLASS_NAME_KEY = "className"
    }
}
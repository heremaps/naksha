package com.here.naksha.lib.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class NakReadRequest(vararg args: Any?) : NakRequest(*args) {

    companion object {
        @JvmStatic
        val LIMIT = Base.intern("limit")
        var DEFAULT_LIMIT = 100_000
    }

    open fun getLimit(): Int = toElement(get(LIMIT), Klass.intKlass) ?: DEFAULT_LIMIT

    open fun setLimit(limit: Int) = set(LIMIT, limit)
}
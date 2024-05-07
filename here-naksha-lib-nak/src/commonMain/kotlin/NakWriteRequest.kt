package com.here.naksha.lib.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class NakWriteRequest(vararg args: Any?) : NakRequest(*args) {

    companion object {
        @JvmStatic
        val NO_RESULTS = Base.intern("noResults")

        @JvmStatic
        val ROWS = Base.intern("rows")
    }

    fun isNoResults(): Boolean = toElement(get(NO_RESULTS), Klass.boolKlass, false)!!

    fun setNoResults(value: Boolean) = set(NO_RESULTS, value)

    fun setRows(values: PArray) = set(ROWS, values)

    fun getRows(): PArray = toElement(get(ROWS), Klass.arrayKlass, Klass.arrayKlass.newInstance())!!
}
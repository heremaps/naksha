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

    fun isNoResults() : Boolean = toElement(get(NO_RESULTS), Klass.boolKlass, false)!!

    fun setNoResults(value: Boolean) = set(NO_RESULTS, value)

    fun setRows(values: BaseList<String>) = set(ROWS, values)

    @Suppress("UNCHECKED_CAST")
    fun getRows(): BaseList<NakWriteRow> = toElement(get(ROWS), BaseList.klass, BaseList())!! as BaseList<NakWriteRow>
}
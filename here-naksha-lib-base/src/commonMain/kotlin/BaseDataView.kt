@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * The Naksha type for a data view.
 */
@JsExport
open class BaseDataView(byteArray: ByteArray? = null, offset: Int? = null, length: Int? = null) : BaseType() {
    init {
        if (byteArray !== null && byteArray !== Base.undefined) {
            val off = offset ?: 0
            val len = length ?: (byteArray.size - off)
            this.data = Base.newDataView(byteArray, off, len)
        }
    }

    companion object {
        @JvmStatic
        val klass = object : BaseDataViewKlass<BaseDataView>() {
            override fun isInstance(o: Any?): Boolean = o is BaseDataView

            override fun newInstance(vararg args: Any?): BaseDataView = BaseDataView()
        }
    }

    override fun klass(): BaseKlass<*> = klass

    override fun data(): PDataView {
        val data = this.data
        if (data !is PDataView) throw IllegalStateException("Missing data object, can't be created on demand")
        return data
    }
}
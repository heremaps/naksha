@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.nak

import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * The Naksha type for a data view.
 */
@JsExport
open class NakDataView(byteArray: ByteArray? = null, offset: Int? = null, length: Int? = null) : NakType() {
    init {
        if (byteArray !== null && byteArray !== Nak.undefined) {
            val off = offset ?: 0
            val len = length ?: (byteArray.size - off)
            this.data = Nak.newDataView(byteArray, off, len)
        }
    }

    companion object {
        @JvmStatic
        val klass = object : NakDataViewKlass<NakDataView>() {
            override fun isInstance(o: Any?): Boolean = o is NakDataView

            override fun newInstance(vararg args: Any?): NakDataView = NakDataView()
        }
    }

    override fun getKlass(): NakKlass<*> = klass

    override fun data(): PDataView {
        val data = this.data
        if (data !is PDataView) throw IllegalStateException("Missing data object, can't be created on demand")
        return data
    }
}
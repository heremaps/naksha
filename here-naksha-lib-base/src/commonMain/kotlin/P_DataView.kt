@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmStatic

/**
 * The Naksha type for a data view.
 */
@JsExport
open class P_DataView() : P() {
    override fun createData(): N_Object {
        TODO("Implement as creating a view with a default size, maybe 4k or alike")
    }
    override fun data(): N_DataView = super.data() as N_DataView

    @JsName("of")
    constructor(byteArray: ByteArray? = null, offset: Int? = null, length: Int? = null): this() {
        if (byteArray !== null && byteArray !== N.undefined) {
            val off = offset ?: 0
            val len = length ?: (byteArray.size - off)
            this.data = N.newDataView(byteArray, off, len)
        }
    }

    companion object {
        @JvmStatic
        val klass = object : OldBaseDataViewKlass<P_DataView>() {
            override fun isInstance(o: Any?): Boolean = o is P_DataView

            override fun newInstance(vararg args: Any?): P_DataView = P_DataView()
        }
    }
}
package com.here.naksha.lib.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@OptIn(ExperimentalJsExport::class)
@JsExport
class NakWriteCollections(vararg args: Any?) : NakWriteRequest(*args) {

    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<NakWriteCollections>() {
            override fun isInstance(o: Any?): Boolean = o is NakWriteCollections

            override fun newInstance(vararg args: Any?): NakWriteCollections = NakWriteCollections(*args)
        }
    }
}
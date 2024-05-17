package com.here.naksha.lib.base

import kotlin.jvm.JvmStatic

class CollectionCounters(vararg args: Any?) : BaseMap<Int>(*args) {

    companion object {
        @JvmStatic
        val klass = object : BaseMapKlass<Int, CollectionCounters>() {
            override fun isInstance(o: Any?): Boolean = o is CollectionCounters

            override fun newInstance(vararg args: Any?): CollectionCounters = CollectionCounters()

        }
    }

}
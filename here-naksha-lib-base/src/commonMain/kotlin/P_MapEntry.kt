@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A simple wrapper for key-value pairs stored in an [PlatformList], with key being stored at index `0` and value at index `1`.
 */
@Suppress("UNCHECKED_CAST")
@JsExport
class P_MapEntry<K, V>() : Proxy() {
    // TODO: Fix this, we need the keyKlass and valueKlass, otherwise automatic casting will not work!
    //       Therefore this needs as well to become an abstract class, we need concrete types!

    override fun createData(): PlatformList = Platform.newArray()
    override fun data(): PlatformList = super.data() as PlatformList

    @JsName("of")
    constructor(key: K, value: V) : this() {
        with(key, value)
    }

    fun with(key: K, value: V?): P_MapEntry<K, V> {
        val data = data()
        data[0] = key
        data[1] = value
        return this
    }

    fun getKey(): K = data()[0] as K
    fun setKey(key: K) {
        data()[0] = key
    }

    fun getValue(): V? = data()[1] as V
    fun setValue(value: V?) {
        data()[1] = value
    }
}
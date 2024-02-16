@file:OptIn(ExperimentalJsExport::class)
package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * The operation to be executed.
 */
@JsExport
class XyzTags : XyzSpecial<XyzTags>() {
    private lateinit var _tagsMap: IMap
    private lateinit var _tagsArray: Array<String>

    override fun parseHeader(mandatory: Boolean) {
        super.parseHeader(mandatory)
        check(variant == XYZ_TAGS)

        val globalDictId = if (reader.isString()) reader.readString() else null
        if (globalDictId != null) {
            reader.globalDict = Jb.env.getGlobalDictionary(globalDictId)
        }

        // Now all key-value pairs follow.
        val map = newMap()
        val array = ArrayList<String>()
        while (reader.nextUnit()) {
            var key: String
            if (reader.isGlobalRef()) {
                val index = reader.readRef()
                key = reader.globalDict!!.get(index)
            } else {
                key = reader.readString()
            }
            check(reader.nextUnit())
            var value: Any?
            if (reader.isNumber()) {
                value = reader.readFloat64()
                if (Jb.env.canBeInt32(value)) {
                    val intValue = value.toInt()
                    array.add("$key:=$intValue")
                } else {
                    array.add("$key:=$value")
                }
            } else if (reader.isBool()) {
                value = reader.readBoolean()
                array.add("$key:=$value")
            } else if (reader.isString()) {
                value = reader.readString()
                array.add("$key=$value")
            } else if (reader.isGlobalRef()) {
                val index = reader.readRef()
                value = reader.globalDict!!.get(index)
                array.add("$key=$value")
            } else {
                value = null
                array.add(key)
            }
            map.put(key, value)
        }
        this._tagsMap = map
        this._tagsArray = array.toTypedArray()
        noContent()
    }

    /**
     * If the tags rely upon a global dictionary, this returns the identifier of this dictionary.
     * @return The identifier of the used global dictionary or _null_, when no global dictionary is used.
     */
    fun globalDictId(): String? = reader.globalDict?.id()

    /**
     * Returns the tags as map.
     */
    fun tagsMap(): IMap = _tagsMap

    /**
     * Returns the tags as array.
     */
    fun tagsArray(): Array<String> = _tagsArray
}
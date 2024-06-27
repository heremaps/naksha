@file:OptIn(ExperimentalJsExport::class)

package naksha.jbon

import naksha.base.ObjectProxy
import naksha.base.AbstractMapProxy
import naksha.base.Platform
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * The operation to be executed.
 * @property dictManager The dictionary manager to use to decode the tags.
 */
@JsExport
class XyzTags(var dictManager: IDictManager) : XyzStruct<XyzTags>() {
    private lateinit var _tagsMap: ObjectProxy
    private lateinit var _tagsArray: Array<String>

    override fun parseHeader() {
        super.parseXyzHeader(XYZ_TAGS_VARIANT)

        val dictId = if (reader.isString()) reader.decodeString() else null
        if (dictId != null) {
            reader.globalDict = dictManager.getDictionary(dictId)
            check(reader.globalDict != null) { "Failed to load dictionary with ID '$dictId'" }
        } else {
            check(reader.isNull()) { "Invalid header, expected null, but found ${JbReader.unitTypeName(reader.unitType())}" }
        }

        // Now all key-value pairs follow.
        val map = ObjectProxy()
        val array = ArrayList<String>()
        while (reader.nextUnit()) {
            var key: String
            if (reader.isGlobalRef()) {
                val index = reader.decodeRef()
                key = reader.globalDict!!.get(index)
            } else {
                key = reader.decodeString()
            }
            check(reader.nextUnit())
            var value: Any?
            if (reader.isNumber()) {
                value = reader.decodeFloat64()
                if (Platform.canBeInt32(value)) {
                    val intValue = value.toInt()
                    array.add("$key:=$intValue")
                } else {
                    array.add("$key:=$value")
                }
            } else if (reader.isBool()) {
                value = reader.readBoolean()
                array.add("$key:=$value")
            } else if (reader.isString()) {
                value = reader.decodeString()
                array.add("$key=$value")
            } else if (reader.isGlobalRef()) {
                val index = reader.decodeRef()
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
    }

    /**
     * If the tags rely upon a global dictionary, this returns the identifier of this dictionary.
     * @return The identifier of the used global dictionary or _null_, when no global dictionary is used.
     */
    fun globalDictId(): String? = reader.globalDict?.id()

    /**
     * Returns the tags as map.
     */
    fun tagsMap(): AbstractMapProxy<String, *> = _tagsMap

    /**
     * Returns the tags as array.
     */
    fun tagsArray(): Array<String> = _tagsArray
}
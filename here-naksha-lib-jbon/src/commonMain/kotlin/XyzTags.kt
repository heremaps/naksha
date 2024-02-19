@file:OptIn(ExperimentalJsExport::class)

import com.here.naksha.lib.jbon.*
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * The operation to be executed.
 */
@JsExport
class XyzTags : XyzSpecial<XyzTags>() {
    private lateinit var tags: IMap

    override fun parseHeader(mandatory: Boolean) {
        super.parseHeader(mandatory)
        check(variant == XYZ_TAGS)

        val globalDictId = if (reader.isString()) reader.readString() else null
        if (globalDictId != null) {
            reader.globalDict = Jb.env.getGlobalDictionary(globalDictId)
        }

        // Now all key-value pairs follow.
        val tags = newMap()
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
            } else if (reader.isBool()) {
                value = reader.readBoolean()
            } else if (reader.isString()) {
                value = reader.readString()
            } else if (reader.isGlobalRef()) {
                val index = reader.readRef()
                value = reader.globalDict!!.get(index)
            } else {
                value = null
            }
            tags.put(key, value)
        }
        this.tags = tags
        noContent()
    }

    /**
     * If the tags rely upon a global dictionary, this returns the identifier of this dictionary.
     * @return The identifier of the used global dictionary or _null_, when no global dictionary is used.
     */
    fun globalDictId(): String? = reader.globalDict?.id()

    /**
     * Returns all the tags.
     */
    fun tags(): IMap = tags
}
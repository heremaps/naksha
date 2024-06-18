package naksha.jbon

import naksha.base.Binary
import naksha.base.BinaryView
import kotlin.js.JsExport

@Suppress("OPT_IN_USAGE")
@JsExport
class JbPath(var dictManager: JbDictManager, private var binaryView: BinaryView) {
    private var jmap: JbMap = JbMap()
    private var feature: JbFeature = JbFeature(dictManager)

    fun getBool(binary: ByteArray, path: String, alternative: Boolean? = null): Boolean? {
        val valueReader = readElement(binary, path)
        if (valueReader != null && valueReader.isBool()) {
            return valueReader.readBoolean() ?: alternative
        }
        return alternative
    }

    fun getInt32(binary: ByteArray, path: String, alternative: Int? = null): Int? {
        val valueReader = readElement(binary, path)
        if (valueReader != null && valueReader.isInt32()) {
            return valueReader.decodeInt32()
        }
        return alternative
    }

    fun getInt64(binary: ByteArray, path: String, alternative: Int? = null): Int? {
        TODO("int64 has to be implemented")
    }

    fun getFloat32(binary: ByteArray, path: String, alternative: Float? = null): Float? {
        val valueReader = readElement(binary, path)
        if (valueReader != null && valueReader.isFloat32()) {
            return valueReader.decodeFloat32()
        }
        return alternative
    }

    fun getDouble(binary: ByteArray, path: String, alternative: Double? = null): Double? {
        val valueReader = readElement(binary, path)
        if (valueReader != null) {
            if (valueReader.isFloat32()) {
                return valueReader.decodeFloat32().toDouble()
            } else if (valueReader.isFloat64()) {
                return valueReader.decodeFloat64()
            }
        }
        return alternative
    }

    fun getString(binary: ByteArray, path: String, alternative: String? = null): String? {
        val valueReader = readElement(binary, path)
        if (valueReader != null && valueReader.isString()) {
            return valueReader.decodeString()
        }
        return alternative
    }

    private fun readElement(elementBytes: ByteArray, path: String): JbReader? {
        if (!binaryView.byteArray.contentEquals(elementBytes)) {
            binaryView = Binary(elementBytes)
            feature = feature.mapBinary(binaryView)
            jmap.mapReader(feature.reader)
        }

        if (feature.reader.isMap()) {
            val pathElements = path.split(".")
            return goToElement(jmap, pathElements)
        }
        return null
    }

    fun goToElement(map: JbMap, path: List<String>): JbReader? {
        if (path.isEmpty()) {
            return null
        }
        val currentLevelKey = path[0]
        if (map.selectKey(currentLevelKey) && map.ok()) {
            return if (map.value().isMap()) {
                val newMap = JbMap().mapReader(map.value())
                goToElement(newMap, path.drop(1))
            } else {
                map.value()
            }
        }
        return null;
    }
}
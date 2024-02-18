package com.here.naksha.lib.jbon

object JbValuesReader {

    fun readMap(jbMap: JbMap): IMap {
        val imap = newMap()
        while (jbMap.next()) {
            imap.put(jbMap.key(), readValue(jbMap.value()))
        }
        return imap
    }

    fun readArray(jbArray: JbArray): Array<Any?> {
        val arr = Array<Any?>(jbArray.length()) {}
        var i = 0
        while (jbArray.next() && jbArray.ok()) {
            arr[i] = readValue(jbArray.value())
            i += 1
        }
        return arr
    }

    fun readValue(reader: JbReader): Any? {
        return if (reader.isInt32()) {
            reader.readInt32()
        } else if (reader.isInt()) {
            reader.readInt64()
        } else if (reader.isString()) {
            reader.readString()
        } else if (reader.isBool()) {
            reader.readBoolean()
        } else if (reader.isFloat32()) {
            reader.readFloat32()
        } else if (reader.isFloat64()) {
            reader.readFloat64()
        } else if (reader.isNull()) {
            null
        } else if (reader.isText()) {
            reader.readText()
        } else if (reader.isTimestamp()) {
            reader.readTimestamp()
        } else if (reader.isMap()) {
            readMap(JbMap().mapReader(reader))
        } else if (reader.isArray()) {
            readArray(JbArray().mapReader(reader))
        } else {
            throw UnsupportedOperationException("Not implemented jbon value type")
        }
    }
}
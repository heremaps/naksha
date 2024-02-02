@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon;

@Suppress("unused", "UnsafeCastFromDynamic")
@JsExport
open class JsSession : JbSession() {

    class JsGetterSession(private val session: JsSession) : IJbThreadLocalSession {

        override fun get(): JsSession {
            return session
        }
    }

    companion object {
        private val nativeMap = JsMap()
        private val nativeList = JsList()
        private val nativeLog = JsLog()
        private val convertView: IDataView = JsSession().newDataView(ByteArray(16))

        fun register(session: JsSession?) {
            if (instance == null) {
                instance = JsGetterSession(session ?: JsSession())
                js("""
var platform = this;
DataView.prototype.getByteArray = function() {
    return new Int8Array(this.buffer);
}
DataView.prototype.getStart = function() {
    return this.byteOffset;
}
DataView.prototype.getEnd = function() {
    return this.byteOffset + this.byteLength;
}
DataView.prototype.getSize = function() {
    return this.byteLength;
}
""")
            }
        }
    }

    /**
     * Convert an internal type identifier into a name.
     * @param type As returned by [JbReader.unitType].
     * @param typeParam As returned by [JbReader.unitTypeParam].
     * @return A human-readable type value.
     */
    fun typeToName(type: Int, typeParam: Int = -1): String {
        return when (type) {
            TYPE_NULL -> "null"
            else -> "eof"
        }
    }

    /**
     * Convert a human-readable type name into the internal type identifier, including optional type-parameter being set.
     * @param typeName The human-readable name as returned by [typeToName].
     * @return The internal type identifier with optional subtype set.
     */
    fun typeFromName(typeName: String): Int {
        return when (typeName) {
            "null" -> TYPE_NULL
            else -> EOF
        }
    }

    override fun map(): INativeMap {
        return nativeMap
    }

    override fun list(): INativeList {
        return nativeList
    }

    override fun sql(): ISql {
        TODO("Not yet implemented")
    }

    override fun log(): INativeLog {
        return nativeLog
    }

    override fun stringify(any: Any, pretty: Boolean): String {
        return js("JSON.stringify(any, pretty)")
    }

    override fun parse(json: String): Any {
        return js("JSON.parse(json)")
    }

    @Suppress("NON_EXPORTABLE_TYPE")
    override fun longToBigInt(value: Long): Any {
        val view = convertView
        view.setInt32(0, (value ushr 32).toInt())
        view.setInt32(4, value.toInt())
        return js("view.getBigInt64(0)")
    }

    @Suppress("NON_EXPORTABLE_TYPE")
    override fun bigIntToLong(value: Any): Long {
        val view = convertView
        js("view.setBigInt64(0, value)")
        val hi = view.getInt32(0)
        val lo = view.getInt32(4)
        return ((hi.toLong() and 0xffff_ffff) shl 32) or (lo.toLong() and 0xffff_ffff)
    }

    @Suppress("UnsafeCastFromDynamic", "UNUSED_VARIABLE")
    override fun newDataView(bytes: ByteArray, offset: Int, size: Int): IDataView {
        require(offset in bytes.indices)
        require(size >= 0)
        var length = if (offset + size <= bytes.size) size else bytes.size - offset
        return js("new DataView(bytes.buffer, offset, length)")
    }

    override fun lz4Deflate(raw: ByteArray, offset: Int, size: Int): ByteArray {
        val end = endOf(raw, offset, size)
        val bytes: ByteArray
        if (offset == 0 && end == raw.size) {
            bytes = raw
        } else {
            bytes = ByteArray(end - offset)
            raw.copyInto(bytes, 0, offset, end)
        }
        return js("lz4.compress(bytes)") as ByteArray
    }

    override fun lz4Inflate(compressed: ByteArray, bufferSize: Int, offset: Int, size: Int): ByteArray {
        return js("lz4.decompress(compressed)") as ByteArray
    }

    override fun getGlobalDictionary(id: String): JbDict {
        TODO("We need an implementation that works in the browser in PLV8 engine, so implemented externally")
    }
}
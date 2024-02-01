@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon;

@Suppress("unused")
@JsExport
open class JsSession : JbSession() {
    class JsGetterSession : IJbThreadLocalSession {
        private val theNative = JsSession()

        override fun get() : JbSession {
            return theNative
        }
    }

    companion object {
        fun register() {
            if (instance == null) {
                instance = JsGetterSession()
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
    fun typeToName(type:Int, typeParam:Int = -1):String {
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
        return when(typeName) {
            "null" -> TYPE_NULL
            else -> EOF
        }
    }

    override fun newMap(): Any {
        return js("{}") as Any
    }

    override fun map(): INativeMap {
        TODO("Not yet implemented")
    }

    override fun sql(): ISql {
        TODO("Not yet implemented")
    }

    override fun log(): INativeLog {
        TODO("Not yet implemented")
    }

    override fun stringify(any: Any, pretty: Boolean): String {
        return js("JSON.stringify(any, pretty)") as String
    }

    override fun parse(json: String): Any {
        return js("JSON.parse(json)") as Any
    }

    @Suppress("NON_EXPORTABLE_TYPE")
    override fun longToBigInt(value: Long): Any {
        // Read the four words unsigned
        val hi = (value shr 32).toInt()
        val lo = (value ushr 0xffff).toInt()
        // Combine them to BigInt
        return js("new BitInt(hi)<<32 | new BigInt(mid)<<16 | new BigInt(lo)") as Any;
    }

    @Suppress("NON_EXPORTABLE_TYPE")
    override fun bigIntToLong(value: Any): Long {
        var hi : Int = 0
        var mid : Int = 0
        var lo : Int = 0
        js("hi = value");
        TODO("Fix me")
        //return (hi.toLong() shl 32) or (mid.toLong() shl 16) or (lo.toLong())
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
        val bytes : ByteArray
        if (offset == 0 && end == raw.size) {
            bytes = raw
        } else {
            bytes = ByteArray(end-offset)
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
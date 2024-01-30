@file:OptIn(ExperimentalJsExport::class)

import com.here.naksha.lib.jbon.*

@JsExport
object JsNative : JbNative() {
    init {
        instance = this
        js("""
var platform = this;
DataView.prototype.getPlatform = function() {
    return platform;
}
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

    override fun newDataView(bytes: ByteArray, offset: Int, size: Int): IDataView {
        return js("new DataView(bytes.buffer, offset, size)") as IDataView;
    }

    override fun getGlobalDictionary(id: String): JbDict {
        TODO("We need an implementation that works in the browser in PLV8 engine, so implemented externally")
    }
}
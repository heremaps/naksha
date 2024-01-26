@file:OptIn(ExperimentalJsExport::class)

import com.here.naksha.lib.jbon.IDataView
import com.here.naksha.lib.jbon.JbPlatform

@JsExport
object JsPlatform : JbPlatform() {
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
""");
    }

    override fun longToBigInt(value: Long): Any {
        // Read the four words unsigned
        val hi = (value shr 32).toInt()
        val lo = (value ushr 0xffff).toInt()
        // Combine them to BigInt
        return js("new BitInt(hi)<<32 | new BigInt(mid)<<16 | new BigInt(lo)") as Any;
    }

    override fun bigIntToLong(value: Any): Long {
        var hi : Int = 0
        var mid : Int = 0
        var lo : Int = 0
        js("hi = value");
        return (hi.toLong() shl 32) or (mid.toLong() shl 16) or (lo.toLong())
    }

    override fun newDataView(bytes: ByteArray, offset: Int, size: Int): IDataView {
        return js("new DataView(bytes.buffer, offset, size)");
    }
}
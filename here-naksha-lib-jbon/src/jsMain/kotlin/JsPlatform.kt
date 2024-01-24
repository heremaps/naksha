@file:OptIn(ExperimentalJsExport::class)

import com.here.naksha.lib.jbon.IDataView
import com.here.naksha.lib.jbon.IPlatform

@JsExport
object JsPlatform : IPlatform {
    init {
        ensureInit()
    }

    fun ensureInit() : JsPlatform {
        var platform = JsPlatform
        js("""
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
        return platform
    }

    override fun dataViewOf(bytes: ByteArray, offset: Int, size: Int): IDataView {
        return js("new DataView(bytes.buffer, offset, size)");
    }
}
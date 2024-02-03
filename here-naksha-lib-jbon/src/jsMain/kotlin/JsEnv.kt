@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon;

/**
 * Javascript environment.
 */
@Suppress("unused", "UnsafeCastFromDynamic", "NON_EXPORTABLE_TYPE", "UNUSED_VARIABLE")
@JsExport
open class JsEnv : IEnv {

    companion object {
        /**
         * Returns the current environment, if it is not yet initialized, initializes it.
         * @return The environment.
         */
        fun get() : JsEnv {
            if (!JbSession.isInitialized()) {
                js("""
DataView.prototype.getByteArray = function() { return new Int8Array(this.buffer); };
DataView.prototype.getStart = function() { return this.byteOffset; };
DataView.prototype.getEnd = function() { return this.byteOffset + this.byteLength; };
DataView.prototype.getSize = function() { return this.byteLength; };""")
                JbSession.initialize(JsThreadLocal(), JsEnv(), JsList(), JsMap(), BrowserLog())
            }
            return JbSession.env as JsEnv
        }

        private val globalDictionaries = HashMap<String, JbDict>()
        private var convertView: IDataView? = null
    }

    override fun stringify(any: Any, pretty: Boolean): String {
        return js("JSON.stringify(any, pretty)")
    }

    override fun parse(json: String): Any {
        return js("JSON.parse(json)")
    }

    override fun epochMillis(): Long {
        val millis = js("Date.now()") as Double
        return millis.toLong()
    }

    override fun random(): Double {
        return js("Math.random()")
    }

    override fun longToBigInt(value: Long): Any {
        var view = convertView
        if (view == null) {
            view = JbSession.env!!.newDataView(ByteArray(16))
            convertView = view
        }
        view.setInt32(0, (value ushr 32).toInt())
        view.setInt32(4, value.toInt())
        return js("view.getBigInt64(0)")
    }

    override fun bigIntToLong(value: Any): Long {
        var view = convertView
        if (view == null) {
            view = JbSession.env!!.newDataView(ByteArray(16))
            convertView = view
        }
        js("view.setBigInt64(0, value)")
        val hi = view.getInt32(0)
        val lo = view.getInt32(4)
        return ((hi.toLong() and 0xffff_ffff) shl 32) or (lo.toLong() and 0xffff_ffff)
    }

    override fun newDataView(bytes: ByteArray, offset: Int, size: Int): IDataView {
        require(offset in bytes.indices)
        require(size >= 0)
        val length = if (offset + size <= bytes.size) size else bytes.size - offset
        return js("new DataView(bytes.buffer, offset, length)")
    }

    override fun lz4Deflate(raw: ByteArray, offset: Int, size: Int): ByteArray {
        throw NotImplementedError("lz4Deflate")
    }

    override fun lz4Inflate(compressed: ByteArray, bufferSize: Int, offset: Int, size: Int): ByteArray {
        throw NotImplementedError("lz4Inflate")
    }

    override fun putGlobalDictionary(dict: JbDict) {
        val id = dict.id()
        check(id != null)
        globalDictionaries[id] = dict
    }

    override fun removeGlobalDictionary(dict: JbDict) {
        val id = dict.id()
        check(id != null)
        if (globalDictionaries[id] === dict) {
            globalDictionaries.remove(id)
        }
    }

    override fun getGlobalDictionary(id: String): JbDict? {
        return globalDictionaries[id]
    }
}
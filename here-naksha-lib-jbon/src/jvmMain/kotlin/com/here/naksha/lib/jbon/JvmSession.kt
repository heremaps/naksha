package com.here.naksha.lib.jbon

import com.here.naksha.lib.core.util.json.Json
import net.jpountz.lz4.LZ4Factory
import sun.misc.Unsafe

open class JvmSession() : JbSession() {
    class JvmGetterSession : ThreadLocal<JbSession>(), IJbThreadLocalSession {
        override fun initialValue(): JbSession {
            return JvmSession()
        }
    }

    companion object {
        val unsafe: Unsafe
        val baseOffset: Int

        init {
            val unsafeConstructor = Unsafe::class.java.getDeclaredConstructor()
            unsafeConstructor.isAccessible = true
            unsafe = unsafeConstructor.newInstance()
            val someByteArray = ByteArray(8)
            baseOffset = unsafe.arrayBaseOffset(someByteArray.javaClass)
        }

        fun register() {
            if (instance == null) instance = JvmGetterSession()
        }
    }

    private val lz4Factory = LZ4Factory.fastestInstance()

    /**
     * The logger implementation to be used.
     */
    internal var log : INativeLog = Slf4jLogger()

    /**
     * The SQL connection to be used.
     */
    var sqlApi : ISql? = null

    override fun newMap(): Any {
        return HashMap<String, Any>()
    }

    override fun map(): INativeMap {
        TODO("Not yet implemented")
    }

    override fun sql(): ISql {
        val sql = this.sqlApi
        check(sql != null)
        return sql
    }

    override fun log(): INativeLog {
        return log
    }

    override fun stringify(any: Any, pretty: Boolean): String {
        Json.get().use {
            if (pretty) {
                return it.writer().withDefaultPrettyPrinter().writeValueAsString(any)
            }
            return it.writer().writeValueAsString(any)
        }
    }

    override fun parse(json: String): Any {
        Json.get().use {
            return it.reader().forType(Object::class.java).readValue(json)
        }
    }

    override fun longToBigInt(value: Long): Any {
        return value
    }

    override fun bigIntToLong(value: Any): Long {
        require(value is Long)
        return value
    }

    override fun newDataView(bytes: ByteArray, offset: Int, size: Int): IDataView {
        if (offset < 0) throw Exception("offset must be greater or equal zero")
        var end = offset + size
        if (end < offset) { // means, size is less than zero!
            end += bytes.size // size is counted from end of array
            if (end < 0) throw Exception("invalid end, must be greater/equal zero")
        }
        // Cap to end of array
        if (end > bytes.size) end = bytes.size
        if (end < offset) throw Exception("end is before start")
        return JvmDataView(bytes, offset + baseOffset, end + baseOffset)
    }

    override fun lz4Deflate(raw: ByteArray, offset: Int, size: Int): ByteArray {
        val end = endOf(raw, offset, size)
        val compressor = lz4Factory.fastCompressor()
        val maxCompressedLength = compressor.maxCompressedLength(end - offset)
        val compressed = ByteArray(maxCompressedLength)
        val compressedLength = compressor.compress(raw, offset, end - offset, compressed, 0, maxCompressedLength)
        return compressed.copyOf(compressedLength)
    }

    override fun lz4Inflate(compressed: ByteArray, bufferSize: Int, offset: Int, size: Int): ByteArray {
        val end = endOf(compressed, offset, size)
        val decompressor = lz4Factory.fastDecompressor()
        val restored = if (bufferSize <= 0) ByteArray((end - offset) * 10) else ByteArray(bufferSize)
        val decompressedLength = decompressor.decompress(compressed, offset, restored, 0, restored.size)
        if (decompressedLength < restored.size) {
            return restored.copyOf(decompressedLength)
        }
        return restored
    }

    override fun getGlobalDictionary(id: String): JbDict {
        TODO("We need a way so that our lib-psql can override this")
    }
}
package com.here.naksha.lib.jbon

import com.here.naksha.lib.core.util.json.Json
import net.jpountz.lz4.LZ4Factory
import sun.misc.Unsafe
import java.sql.Connection

open class JvmSession : JbSession() {
    private class JvmSessionGetter : ThreadLocal<JvmSession>(), IJbThreadLocalSession {
        override fun initialValue(): JvmSession {
            return JvmSession()
        }
    }

    companion object {
        val unsafe: Unsafe
        val baseOffset: Int
        @JvmStatic
        private val nativeLog: INativeLog = Slf4jLogger()
        private val nativeMap = JvmMap()
        private val nativeList = JvmList()

        init {
            val unsafeConstructor = Unsafe::class.java.getDeclaredConstructor()
            unsafeConstructor.isAccessible = true
            unsafe = unsafeConstructor.newInstance()
            val someByteArray = ByteArray(8)
            baseOffset = unsafe.arrayBaseOffset(someByteArray.javaClass)
        }

        @JvmStatic
        fun register() : JvmSession {
            if (instance == null) {
                instance = JvmSessionGetter()
            }
            return get()
        }

        @JvmStatic
        fun get(): JvmSession {
            return instance!!.get() as JvmSession
        }
    }

    private val lz4Factory = LZ4Factory.fastestInstance()

    protected var jvmSql: JvmSql? = null

    override fun map(): INativeMap {
        return nativeMap
    }

    override fun list(): INativeList {
        return nativeList
    }

    override fun sql(): ISql {
        val sql = this.jvmSql
        check(sql != null)
        return sql
    }

    /**
     * Sets the SQL connection to be used for the SQL interface.
     * @return this.
     */
    open fun setConnection(conn: Connection?): JvmSession {
        val existing = jvmSql
        if (existing != null) {
            if (existing.conn === conn) {
                return this
            }
            existing.conn.close()
            jvmSql = null
        }
        jvmSql = if (conn == null) null else JvmSql(conn)
        return this
    }

    /**
     * Returns the JDBC connection of this session.
     */
    fun getConnection(): Connection {
        val sql = sql()
        check(sql is JvmSql)
        return sql.conn
    }

    /**
     * Only the JVM session allows to commit the connection, requires an [JvmSql] instance.
     */
    fun commit() {
        val sql = sql()
        check(sql is JvmSql)
        sql.conn.commit()
    }

    /**
     * Only the JVM session allows to roll back the connection, requires an [JvmSql] instance.
     */
    fun rollback() {
        val sql = sql()
        check(sql is JvmSql)
        sql.conn.rollback()
    }

    override fun log(): INativeLog {
        return nativeLog
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

    private fun getResourceAsText(path: String): String? =
            object {}.javaClass.getResource(path)?.readText()

    private fun applyReplacements(text: String, replacements: Map<String, String>?): String {
        if (replacements != null) {
            var t = text
            val sb = StringBuilder()
            for (entry in replacements) {
                sb.setLength(0)
                sb.append('$').append('{').append(entry.key).append('}')
                t = t.replace(sb.toString(), entry.value, true)
            }
            return t
        } else {
            return text
        }
    }

    /**
     * Execute the SQL being in the file.
     * @param path The file-path, for example `/lz4.sql`.
     * @param replacements A map of replacements (`${name}`) that should be replaced with the given value in the source.
     */
    fun executeSqlFromResource(path: String, replacements: Map<String, String>? = null) {
        sql().execute(applyReplacements(getResourceAsText(path)!!, replacements))
    }

    /**
     * Install a JS module with the given name from the given resource file.
     * @param name The module name, for example `lz4`.
     * @param path The file-path, for example `/lz4.js`.
     * @param autoload If the module should be automatically loaded.
     * @param extraCode Additional code to be executed, appended at the end of the module.
     * @param replacements A map of replacements (`${name}`) that should be replaced with the given value in the source.
     */
    fun installModuleFromResource(name: String, path: String, autoload: Boolean = false, extraCode: String? = null, replacements: Map<String, String>? = null) {
        val sql = sql()
        var code = applyReplacements(getResourceAsText(path)!!, replacements)
        if (extraCode != null) code += extraCode
        sql.execute("INSERT INTO commonjs2_modules (module, autoload, source) VALUES ($1, $2, $3) " +
                "ON CONFLICT (module) DO UPDATE SET autoload = $2, source = $3",
                name, autoload, code)
    }

    /**
     * Installs the commonjs2 code and all modules. Must only be executed ones per storage.
     * @param replacements A map of replacements (`${name}`) that should be replaced with the given value in the source.
     */
    open fun installModules(replacements: Map<String, String>? = null) {
        // Note: We know, that we do not need the replacements and code is faster without them!
        executeSqlFromResource("/commonjs2.sql")
        installModuleFromResource("lz4_util", "/lz4_util.js")
        installModuleFromResource("lz4_xxhash", "/lz4_xxhash.js")
        installModuleFromResource("lz4", "/lz4.js")
        executeSqlFromResource("/lz4.sql")
        installModuleFromResource("jbon", "/here-naksha-lib-jbon.js", extraCode = """
                   
                   let input = module.exports["here-naksha-lib-jbon"].com.here.naksha.lib.jbon;
                   const prototypeDescriptors = Object.getOwnPropertyDescriptors(Object.getPrototypeOf(input));
                   const protoClone = Object.create(null, prototypeDescriptors);
                   module.exports = Object.create(protoClone, Object.getOwnPropertyDescriptors(input));
                """.trimIndent());
        executeSqlFromResource("/jbon.sql")
    }
}
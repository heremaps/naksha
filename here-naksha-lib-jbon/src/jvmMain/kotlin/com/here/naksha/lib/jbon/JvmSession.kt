package com.here.naksha.lib.jbon

import com.here.naksha.lib.core.util.json.Json
import net.jpountz.lz4.LZ4Factory
import sun.misc.Unsafe
import java.sql.Connection

open class JvmSession() : JbSession() {
    class JvmGetterSession : ThreadLocal<JvmSession>(), IJbThreadLocalSession {
        override fun initialValue(): JvmSession {
            return JvmSession()
        }
    }

    companion object {
        val unsafe: Unsafe
        val baseOffset: Int
        val jvmGetter = JvmGetterSession()

        init {
            val unsafeConstructor = Unsafe::class.java.getDeclaredConstructor()
            unsafeConstructor.isAccessible = true
            unsafe = unsafeConstructor.newInstance()
            val someByteArray = ByteArray(8)
            baseOffset = unsafe.arrayBaseOffset(someByteArray.javaClass)
        }

        fun register() {
            if (instance == null) instance = jvmGetter
        }
    }

    private val lz4Factory = LZ4Factory.fastestInstance()

    /**
     * The logger implementation to be used.
     */
    internal var log: INativeLog = Slf4jLogger()

    /**
     * The SQL connection to be used.
     */
    private var sqlApi: ISql? = null

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

    /**
     * Sets the SQL implementation.
     * @return this.
     */
    fun sqlSet(sql: ISql?): JvmSession {
        this.sqlApi = sql
        return this
    }

    /**
     * Returns the JDBC connection of this session.
     */
    fun sqlConnection(): Connection {
        val sql = sql()
        check(sql is JvmSql)
        return sql.conn
    }

    /**
     * Only the JVM session allows to commit the connection, requires an [JvmSql] instance.
     */
    fun sqlCommit() {
        val sql = sql()
        check(sql is JvmSql)
        sql.conn.commit()
    }

    /**
     * Only the JVM session allows to roll back the connection, requires an [JvmSql] instance.
     */
    fun sqlRollback() {
        val sql = sql()
        check(sql is JvmSql)
        sql.conn.rollback()
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

    /**
     * Execute the SQL being in the file.
     * @param path The file-path, for example `/lz4.sql`.
     */
    fun executeSqlFromResource(path: String) {
        sql().execute(JvmSession::class.java.getResource(path)!!.readText(), arrayOf())
    }

    /**
     * Install a JS module with the given name from the given resource file.
     * @param name The module name, for example `lz4`.
     * @param path The file-path, for example `/lz4.js`.
     * @param autoload If the module should be automatically loaded.
     * @param extraCode Additional code to be executed, appended at the end of the module.
     */
    fun installModuleFromResource(name: String, path: String, autoload: Boolean = false, extraCode: String? = null) {
        val sql = sql()
        var code = JvmSession::class.java.getResource(path)!!.readText()
        if (extraCode != null) code += extraCode
        sql.execute("INSERT INTO commonjs2_modules (module, autoload, source) VALUES ($1, $2, $3) " +
                "ON CONFLICT (module) DO UPDATE SET autoload = $2, source = $3",
                arrayOf(name, autoload, code))
    }

    /**
     * Installs the commonjs2 code and all modules. Must only be executed ones per storage.
     */
    open fun installModules() {
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
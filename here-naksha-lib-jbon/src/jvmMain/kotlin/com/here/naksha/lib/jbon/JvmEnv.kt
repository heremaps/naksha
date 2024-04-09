package com.here.naksha.lib.jbon

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonFactoryBuilder
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import net.jpountz.lz4.LZ4Factory
import sun.misc.Unsafe
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom

/**
 * The environment being available in the JVM, using a PostgresQL database.
 */
@Suppress("DuplicatedCode", "MemberVisibilityCanBePrivate")
open class JvmEnv : IEnv {

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

        val lz4Factory: LZ4Factory = LZ4Factory.fastestInstance()
        val threadLocal = JvmThreadLocal()
        val objectMapper = ThreadLocal.withInitial {
            val jsonFactory = JsonFactoryBuilder()
                    .configure(JsonFactory.Feature.INTERN_FIELD_NAMES, false)
                    .configure(JsonFactory.Feature.CANONICALIZE_FIELD_NAMES, false)
                    .configure(JsonFactory.Feature.USE_THREAD_LOCAL_FOR_BUFFER_RECYCLING, true)
                    .build()
            jsonFactory.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false)
            jsonFactory.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, false)
            return@withInitial JsonMapper.builder(jsonFactory)
                    .enable(MapperFeature.DEFAULT_VIEW_INCLUSION)
                    .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                    .enable(MapperFeature.SORT_CREATOR_PROPERTIES_FIRST)
                    .serializationInclusion(JsonInclude.Include.NON_NULL)
                    .visibility(PropertyAccessor.SETTER, JsonAutoDetect.Visibility.ANY)
                    .visibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.PUBLIC_ONLY)
                    .visibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE)
                    .visibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.ANY)
                    .configure(SerializationFeature.CLOSE_CLOSEABLE, false)
                    .addModule(JvmJsonModule())
                    .build()
        }

        /**
         * Initializes the environment.
         */
        @JvmStatic
        fun initialize() {
            if (!Jb.isInitialized()) Jb.initialize(JvmEnv(), JvmMapApi(), JvmBigInt64Api(), Slf4jLogger())
        }

        /**
         * Returns the current environment, if not available, initializes it and then returns it. Must be called
         * at least ones before using [JbSession] in a JVM.
         */
        @JvmStatic
        fun get(): JvmEnv {
            if (!Jb.isInitialized()) initialize()
            return Jb.env as JvmEnv
        }
    }

    override fun stringify(any: Any?, pretty: Boolean): String {
        val writer = objectMapper.get().writer()
        return (if (pretty) writer.withDefaultPrettyPrinter() else writer).writeValueAsString(any)
    }

    override fun parse(json: String): Any {
        return objectMapper.get().reader().forType(Object::class.java).readValue(json)
    }

    fun <T> convert(value: Any, toValueType: Class<T>): T {
        return objectMapper.get().convertValue(value, toValueType)
    }

    override fun canBeFloat32(value: Double): Boolean {
        // IEEE-754, 32-bit = One sign-bit, 8-bit exponent biased by 127, then 23-bit mantissa
        // IEEE-754, 64-bit = One sign-bit, 11-bit exponent biased by 1023, then 52-bit mantissa
        // E = 0 means denormalized number (M>0) or null (M=0)
        // E = 255|2047 means either endless (M=0) or not a number (M>0)
        val binary = value.toRawBits()
        var exponent = (binary ushr 52).toInt() and 0x7ff
        if (exponent == 0 || exponent == 2047) return false
        // Remove bias: -1023 (0) .. 1024 (2047)
        exponent -= 1023
        // 32-bit exponent is 8-bit with bias 127: -127 (0) .. 128 (255)
        // We want to avoid extremes as they encode special states.
        if (exponent < -126 || exponent > 127) return false
        // We do not want to lose precision in mantissa either.
        // Either the lower 29-bit of mantissa are zero (only 23-bit used) or all bits are set.
        val mantissa = binary and 0x000f_ffff_ffff_ffff
        return (mantissa and 0x0000_0000_1fff_ffff) == 0L || mantissa == 0x000f_ffff_ffff_ffff
    }

    override fun newThreadLocal(): IThreadLocal {
        return JvmThreadLocal()
    }

    override fun currentMillis(): BigInt64 = JvmBigInt64(System.currentTimeMillis())

    override fun currentMicros(): BigInt64 = JvmBigInt64(System.nanoTime() / 1000)

    override fun random(): Double {
        return ThreadLocalRandom.current().nextDouble()
    }

    override fun newDataView(bytes: ByteArray, offset: Int, size: Int): IDataView {
        val end = endOf(bytes, offset, size) // offset + size
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
}
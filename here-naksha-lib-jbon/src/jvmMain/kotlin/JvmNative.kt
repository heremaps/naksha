import com.here.naksha.lib.core.util.json.Json
import com.here.naksha.lib.jbon.*
import sun.misc.Unsafe

object JvmNative : JbNative() {
    val unsafe: Unsafe
    val baseOffset: Int

    init {
        instance = this
        val unsafeConstructor = Unsafe::class.java.getDeclaredConstructor()
        unsafeConstructor.isAccessible = true
        unsafe = unsafeConstructor.newInstance()
        val someByteArray = ByteArray(8)
        baseOffset = unsafe.arrayBaseOffset(someByteArray.javaClass)
    }

    override fun newMap(): Any {
        return HashMap<String, Any>()
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

    override fun getGlobalDictionary(id: String): JbDict {
        TODO("We need a way so that our lib-psql can override this")
    }
}
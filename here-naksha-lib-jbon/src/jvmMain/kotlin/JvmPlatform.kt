import com.here.naksha.lib.jbon.IDataView
import com.here.naksha.lib.jbon.IPlatform
import sun.misc.Unsafe

object JvmPlatform : IPlatform {
    val unsafe: Unsafe
    val baseOffset: Int

    init {
        val unsafeConstructor = Unsafe::class.java.getDeclaredConstructor()
        unsafeConstructor.isAccessible = true
        unsafe = unsafeConstructor.newInstance()
        val someByteArray = ByteArray(8)
        baseOffset = unsafe.arrayBaseOffset(someByteArray.javaClass)
    }

    override fun dataViewOf(bytes: ByteArray, offset: Int, size: Int): IDataView {
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
}
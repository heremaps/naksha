package naksha.model

import naksha.base.NakshaException
import naksha.base.PlatformDataView
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_get_int8
import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE

/**
 * A reader for C-strings, so ASCII-zero terminated string. Empty strings are represented as _null_.
 * @since 3.0.0
 */
internal class CStringReader(
    /**
     * The view to read from.
     * @since 3.0.0
     */
    val view: PlatformDataView,

    /**
     * The byte-offset in the view to start reading at.
     * @since 3.0.0
     */
    var offset: Int
) {
    private var buffer = ByteArray(256)

    /**
     * Read the next string, move [offset] forward.
     * @param name the name of the string read, only used in error case.
     * @return the read string or _null_, if the string is of zero length.
     */
    fun readNext(name: String): String? {
        var buffer = this.buffer
        var pos = offset
        var i = 0
        var b: Byte
        while (true) {
            b = dataview_get_int8(view, pos++)
            if (b != 0.toByte()) {
                if (i >= 16777215) throw NakshaException(ILLEGAL_STATE, "$name too long")
                if (i > buffer.size) {
                    // 256, 1024, 4096, 16384, 65536, 262144, 1048576, 4194304, 16777216 (max)
                    buffer = buffer.copyOf(buffer.size * 4)
                }
                buffer[i++] = b
            } else break
        }
        this.buffer = buffer
        this.offset = pos
        return if (i > 0) buffer.decodeToString(0, i) else null
    }
}
package naksha.base

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object GZip {

    fun gzip(raw: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(raw) }
        return bos.toByteArray()
    }

    fun gunzip(raw: ByteArray): ByteArray =
            GZIPInputStream(raw.inputStream()).use { it.readBytes() }
}
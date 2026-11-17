package naksha.psql

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PgCursorBinaryJsTest {
  @Test
  fun arrayBuffersAreExposedAsSignedBytesWithoutCopying() {
    val buffer = buffer()
    cursor(buffer).use { cursor ->
      val value = cursor.column("payload")
      assertTrue(value is ByteArray)
      assertBytes(value, buffer, 0, 32)
    }
  }

  @Test
  fun everyViewIsReinterpretedAsItsExactByteWindow() {
    val buffer = buffer()
    val views = arrayOf(
      js("new Int8Array(buffer, 3, 7)"),
      js("new Uint8Array(buffer, 3, 7)"),
      js("new Uint8ClampedArray(buffer, 3, 7)"),
      js("new Int16Array(buffer, 2, 3)"),
      js("new Uint16Array(buffer, 2, 3)"),
      js("new Int32Array(buffer, 4, 2)"),
      js("new Uint32Array(buffer, 4, 2)"),
      js("new Float32Array(buffer, 8, 2)"),
      js("new Float64Array(buffer, 8, 2)"),
      js("new BigInt64Array(buffer, 8, 2)"),
      js("new BigUint64Array(buffer, 8, 2)"),
      js("new DataView(buffer, 5, 11)")
    )
    for (view in views) {
      cursor(view).use { cursor ->
        val value = cursor.column("payload")
        assertTrue(value is ByteArray)
        assertBytes(value, buffer, view.byteOffset.unsafeCast<Int>(), view.byteLength.unsafeCast<Int>())
      }
    }
  }

  @Test
  fun ordinaryArraysAndScalarValuesAreNotChanged() {
    val array = js("[1, 2, 3]")
    cursor(array).use { assertSame(array, it.column("payload")) }
    cursor("text").use { assertEquals("text", it.column("payload")) }
    cursor(null).use { assertEquals(null, it.column("payload")) }
  }

  private fun buffer(): dynamic {
    val buffer = js("new ArrayBuffer(32)")
    val bytes = js("new Uint8Array(buffer)")
    for (i in 0 until 32) bytes[i] = (i * 17 + 128) and 255
    return buffer
  }

  private fun cursor(value: dynamic): PgCursor {
    val row = js("({})")
    row.payload = value
    return PgCursor(arrayOf(row)).fetch()
  }

  private fun assertBytes(bytes: ByteArray, buffer: dynamic, offset: Int, length: Int) {
    assertEquals(length, bytes.size)
    assertSame(buffer, bytes.asDynamic().buffer)
    assertEquals(offset, bytes.asDynamic().byteOffset)
    for (i in 0 until length) assertEquals(((offset + i) * 17 + 128).toByte(), bytes[i])
  }
}

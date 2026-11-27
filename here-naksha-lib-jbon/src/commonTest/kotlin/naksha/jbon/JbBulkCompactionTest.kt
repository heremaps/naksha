package naksha.jbon

import naksha.base.AnyObject
import naksha.base.Platform
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class JbBulkCompactionTest {
  @Test
  fun compactionPreservesMappedOffsetsAndBoundarySizedStrings() {
    for (length in listOf(0, 1, 60, 61, 316, 317, 65535, 65536)) {
      val text = "x".repeat(length)
      val feature = AnyObject().apply { setRaw("id", "bulk"); setRaw("text", text) }
      val expected = JbEncoder2().buildTupleFromMap(feature)
      val backing = ByteArray(length * 3 + 4096) { 77 }
      val offset = 11
      val encoder = JbEncoder2().apply {
        view = Platform.newDataView(backing, offset, backing.size - offset - 13)
        resize = false
      }
      val actual = encoder.buildTupleFromMap(feature)
      assertContentEquals(expected, actual, "length=$length")
      for (i in 0 until offset) assertEquals(77.toByte(), backing[i], "Unmapped prefix must not change")
      for (i in backing.size - 13 until backing.size) assertEquals(77.toByte(), backing[i], "Unmapped suffix must not change")
      val decoder = JbDecoder2()
      decoder.mapBytes(actual)
      assertEquals(text, decoder.toAnyObject().getRaw("text"))
    }
  }

  @Test
  fun nestedUnicodeAndReferenceTextSurviveOverlappingCompaction() {
    val feature = AnyObject().apply {
      setRaw("id", "bulk")
      setPath("Grüße αβ 中文 😀", "nested", "text")
    }
    val global = HeapBook(BookType.GLOBAL_BOOK).apply { put("alpha", "alpha"); put("beta", "beta") }
    feature.setPath("alpha beta", "nested", "reference")
    val encoder = JbEncoder2(global)
    val bytes = encoder.buildTupleFromMap(feature)
    val decoder = JbDecoder2(globalBook = global)
    decoder.mapBytes(bytes)
    val decoded = decoder.toAnyObject()
    assertEquals("Grüße αβ 中文 😀", decoded.getPath("nested", "text"))
    assertEquals("alpha beta", decoded.getPath("nested", "reference"))
    assertContentEquals(bytes, encoder.buildTupleFromMap(feature), "Dictionary/reset and overlap handling are repeatable")
  }
}

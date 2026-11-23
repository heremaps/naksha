package naksha.jbon

import naksha.base.AnyObject
import naksha.base.NakshaException
import naksha.base.AnyList
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JbMemberReferenceTest {
  @Test
  fun builderSnapshotsKeepFirstMatchAndDoNotRetainInputArrays() {
    val path = arrayOf<Any?>("properties", "value")
    val builder = JbMemberReferenceBuilder().add(path, 3)
    val before = builder.build()
    path[1] = "changed"
    builder.add(arrayOf("properties", "value"), 7).add(arrayOf("other"), 5)
    val after = builder.build()
    assertEquals(3, before.memberIndex(arrayOf("properties", "value")))
    assertEquals(-1, before.memberIndex(arrayOf("other")))
    assertEquals(3, after.memberIndex(arrayOf("properties", "value")))
    assertEquals(5, after.memberIndex(arrayOf("other")))
    assertFailsWith<NakshaException> { builder.add(arrayOf(null), 0) }
    assertFailsWith<NakshaException> { builder.add(arrayOf("value"), -2) }
    assertFailsWith<NakshaException> { builder.add(arrayOf(1.5), 0) }
    assertFailsWith<NakshaException> { builder.add(arrayOf(1L), 0) }
    assertFailsWith<NakshaException> { builder.addList(AnyList().apply { add(null) }, 0) }
  }

  @Test
  fun sharedPlansHaveIndependentEncoderStateAndResetAfterUnmatchedBranches() {
    val plan = JbMemberReferenceBuilder().add(arrayOf("properties", "value"), 0).build()
    val feature = AnyObject().apply {
      setPath("deep", "unmatched", "nested", "value")
      setPath("original", "properties", "value")
      setRaw("id", "reference")
    }
    val first = JbEncoder2().withMemberReferences(plan)
    val second = JbEncoder2().withMemberReferences(plan)
    val bytes = first.buildTupleFromMap(feature)
    assertContentEquals(bytes, second.buildTupleFromMap(feature))
    assertContentEquals(bytes, first.buildTupleFromMap(feature))
    val book = HeapBook(BookType.MEMBER_BOOK).apply { put("value", "replacement") }
    val decoder = JbDecoder2(membersBook = book)
    decoder.mapBytes(bytes)
    val decoded = decoder.toAnyObject()
    assertEquals("replacement", decoded.getPath("properties", "value"))
    assertEquals("deep", decoded.getPath("unmatched", "nested", "value"))
    assertEquals(0, first.pathEnd)
    first.withMemberReferences(null)
    val plain = JbDecoder2()
    plain.mapBytes(first.buildTupleFromMap(feature))
    assertEquals("original", plain.toAnyObject().getPath("properties", "value"))
  }

  @Test
  fun exceptionsRestoreTraversalAndNextBuildClearsDictionaryState() {
    val plan = JbMemberReferenceBuilder().add(arrayOf("missing"), -1).build()
    val encoder = JbEncoder2().withMemberReferences(plan)
    assertFailsWith<NakshaException> { encoder.buildTupleFromMap(AnyObject().apply { setRaw("missing", "value") }) }
    assertEquals(0, encoder.pathEnd)
    val feature = AnyObject().apply { setRaw("present", "value") }
    val decoder = JbDecoder2()
    decoder.mapBytes(encoder.buildTupleFromMap(feature))
    assertEquals("value", decoder.toAnyObject().getRaw("present"))
  }

  @Test
  fun prefixReferencesShortCircuitAndStringIndexesRemainDistinct() {
    val plan = JbMemberReferenceBuilder().add(arrayOf("p"), 0).add(arrayOf("p", "child"), 1)
      .add(arrayOf("array", 0), 2).add(arrayOf("array", "0"), 3).build()
    assertEquals(0, plan.memberIndex(arrayOf("p")))
    assertEquals(1, plan.memberIndex(arrayOf("p", "child")))
    assertEquals(2, plan.memberIndex(arrayOf("array", 0)))
    assertEquals(3, plan.memberIndex(arrayOf("array", "0")))
    val feature = AnyObject().apply { setPath("not encoded", "p", "child") }
    val book = HeapBook(BookType.MEMBER_BOOK).apply { put("p", "parent"); put("child", "child") }
    val decoder = JbDecoder2(membersBook = book)
    decoder.mapBytes(JbEncoder2().withMemberReferences(plan).buildTupleFromMap(feature))
    assertEquals("parent", decoder.toAnyObject().getRaw("p"))
  }

  @Test
  fun explicitValueRootReferencesDoNotChangeTupleObjectRootBehavior() {
    val plan = JbMemberReferenceBuilder().add(emptyArray(), 0).add(arrayOf("x"), 1).build()
    val encoder = JbEncoder2().withMemberReferences(plan)
    val feature = AnyObject().apply { setRaw("x", "original") }
    encoder.encodeValue(feature)
    val expected = JbEncoder2().apply { encodeRef(0, JB2_REF_BOOK_MEMBERS) }
    assertContentEquals(ByteArray(expected.end) { expected.getInt8(it) }, ByteArray(encoder.end) { encoder.getInt8(it) })
    val book = HeapBook(BookType.MEMBER_BOOK).apply { put("root", "root"); put("x", "replacement") }
    val decoder = JbDecoder2(membersBook = book)
    decoder.mapBytes(encoder.buildTupleFromMap(feature))
    assertEquals("replacement", decoder.toAnyObject().getRaw("x"))
  }

  @Test
  fun referenceBindingsCannotChangeInAnActiveTraversal() {
    val encoder = JbEncoder2().withMemberReferences(JbMemberReferenceBuilder().add(arrayOf("a"), 0).build())
    encoder.pushPath("a")
    assertFailsWith<NakshaException> { encoder.withMemberReferences(null) }
    encoder.popPath()
    encoder.withMemberReferences(null)
    assertEquals(0, encoder.pathEnd)
  }
}

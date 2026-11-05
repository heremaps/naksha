package naksha.model

import naksha.base.TupleNumber
import naksha.base.Version
import naksha.jbon.BookType
import naksha.jbon.HeapBook
import naksha.model.objects.StandardMembers
import naksha.model.request.FeatureTuple
import kotlin.test.*

class FeatureTupleLoadHookTest {
  @Test
  fun heapLoadAppliesHookWithoutChangingCachedTuple() {
    val cache = TupleHeapCache()
    val tuple = tuple()
    cache.put(tuple)
    val reference = SuccessorReference(tuple.tupleNumber)
    assertEquals(1, cache.load(listOf(reference)))
    assertEquals(7L, assertNotNull(reference.tuple).nextVersion)
    assertSame(tuple.featureBytes, reference.tuple?.featureBytes)
    assertSame(cache, reference.source)
    assertEquals(Version.HEAD.number, tuple.nextVersion)
  }

  @Test
  fun cacheBoundaryAppliesHookToAssignedTupleAndRespectsSlice() {
    val tuple = tuple()
    val cache = TupleCache()
    val before = SuccessorReference(tuple.tupleNumber)
    val loaded = SuccessorReference(tuple.tupleNumber)
    val after = SuccessorReference(tuple.tupleNumber)
    loaded.tuple = tuple
    cache.load(listOf(before, loaded, after), from = 1, to = 2, maxMicros = -1L, loadFromStorage = false)
    assertNull(before.tuple)
    assertNull(after.tuple)
    assertEquals(7L, assertNotNull(loaded.tuple).nextVersion)
    assertEquals(Version.HEAD.number, tuple.nextVersion)
  }

  @Test
  fun tupleRemainsPublicJvmField() {
    val tuple = tuple()
    val reference = FeatureTuple(tuple.tupleNumber, null)
    val field = FeatureTuple::class.java.getField("tuple")
    field.set(reference, tuple)
    assertSame(tuple, reference.tuple)
  }

  private fun tuple(): Tuple {
    val members = HeapBook(BookType.MEMBER_BOOK)
    members.put(StandardMembers.TN, TupleNumber(-783475239L, 0, 0, 1, 4L))
    return Tuple(byteArrayOf(1), members)
  }

  private class SuccessorReference(tn: TupleNumber) : FeatureTuple(tn, null) {
    override fun onTupleLoaded() {
      val loaded = tuple ?: return
      if (loaded.nextVersion == 7L) return
      val members = HeapBook.copyOf(loaded.membersBook)
      members.put(StandardMembers.NEXT_VERSION, 7L)
      tuple = Tuple(loaded.featureBytes, members)
    }
  }
}

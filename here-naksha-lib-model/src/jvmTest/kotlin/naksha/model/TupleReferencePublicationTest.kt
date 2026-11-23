package naksha.model

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import naksha.jbon.BookType
import naksha.jbon.HeapBook
import naksha.model.objects.JsonPath
import naksha.model.objects.Member
import naksha.model.objects.MemberList
import naksha.model.objects.MemberType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TupleReferencePublicationTest {
  @Test
  fun concurrentReadOnlyEncodersSeeFullyPublishedImmutablePlans() {
    val members = MemberList(Member("a", MemberType.STRING, JsonPath("properties", "a")),
      Member("b", MemberType.STRING, JsonPath("properties", "b")))
    // Prime proxy defaults before sharing read-only declarations; concurrent mutation is not part of the proxy contract.
    for (member in members) {
      member!!.name
      member.isVirtual()
      member.path
    }
    val book = HeapBook(BookType.MEMBER_BOOK).apply { put("b", "b"); put("a", "a") }
    val start = CountDownLatch(1)
    val pool = Executors.newFixedThreadPool(4)
    try {
      val futures = ArrayList<java.util.concurrent.Future<Unit>>()
      for (i in 0 until 4) futures.add(pool.submit(Callable {
        assertTrue(start.await(10, TimeUnit.SECONDS))
        for (call in 0 until 100) {
          val plan = members.memberReferencePlan(book)
          assertEquals(1, plan.memberIndex(arrayOf("properties", "a")))
          assertEquals(0, plan.memberIndex(arrayOf("properties", "b")))
        }
      }))
      start.countDown()
      for (future in futures) future.get(30, TimeUnit.SECONDS)
    } finally {
      pool.shutdownNow()
      assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
    }
  }
}

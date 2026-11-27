package naksha.base

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ParallelResultsTest {
  @Test
  fun waitsForSlowResultAfterAnotherInputReturnsNull() {
    val started = CountDownLatch(1)
    val release = CountDownLatch(1)
    val fast = CountDownLatch(1)
    val executor = Executors.newSingleThreadExecutor()
    try {
      val result = executor.submit<Array<String?>> {
        Platform.runParallel(arrayOf(0, 1), arrayOfNulls<String>(2)) { input ->
          if (input == 0) {
            fast.countDown()
            null
          } else {
            started.countDown()
            assertTrue(release.await(10, TimeUnit.SECONDS))
            "slow"
          }
        }
      }
      assertTrue(started.await(5, TimeUnit.SECONDS))
      assertTrue(fast.await(5, TimeUnit.SECONDS))
      assertFailsWith<TimeoutException> { result.get(250, TimeUnit.MILLISECONDS) }
      release.countDown()
      assertContentEquals(arrayOf(null, "slow"), result.get(5, TimeUnit.SECONDS))
    } finally {
      release.countDown()
      executor.shutdownNow()
    }
  }
}

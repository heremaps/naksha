package naksha.base

import naksha.base.fn.Fn1
import naksha.base.fn.Fn2
import java.lang.reflect.Modifier
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AttributableConcurrencyTest {
  private class Owner(val payload: String = "owner") : Attributable()
  private class Value(key: String) : Attribute(key) {
    val payload = IntArray(16) { it + 42 }
  }
  private class Cyclic(key: String, val owner: Attributable) : Attribute(key)
  private class Counter(key: String, val count: Int) : Attribute(key)

  @Test
  fun cacheRemainsLazyTransientAndNonVolatileEvenDuringSerialization() {
    val field = Attributable::class.java.getDeclaredField("_attributes").apply { isAccessible = true }
    assertTrue(Modifier.isTransient(field.modifiers))
    assertFalse(Modifier.isVolatile(field.modifiers))
    val owner = Owner()
    assertNull(field.get(owner))
    val before = Platform.toJSON(owner)
    assertNull(field.get(owner), "Serialization must not invoke the attributes getter")
    owner.attributes["cycle"] = Cyclic("cycle", owner)
    assertEquals(before, Platform.toJSON(owner))
    val id = Id(42L)
    val serialized = Platform.toJSON(id)
    assertNull(field.get(id))
    id.attributes["id-cycle"] = Cyclic("id-cycle", id)
    assertEquals(serialized, Platform.toJSON(id))
    assertTrue(Modifier.isFinal(AttributeMap::class.java.getDeclaredField("backing").modifiers),
      "The final backing-map reference supplies construction-time safety for plain cache reads")
  }

  @Test
  fun racingInitializersReturnOneFullyConstructedMap() {
    repeat(50) {
      val owner = Owner()
      val observed = arrayOfNulls<AttributeMap>(8)
      runConcurrent { worker ->
        val map = owner.attributes
        observed[worker] = map
        for (i in 0 until 32) {
          assertSame(map, owner.attributes)
          val value = map.getOrCreate("worker_$worker", Fn1 { Value(it) })
          for (j in value.payload.indices) assertEquals(j + 42, value.payload[j])
        }
      }
      val canonical = owner.attributes
      for (map in observed) assertSame(canonical, map)
      assertEquals(8, canonical.size)
    }
  }

  @Test
  fun racingValueFactoriesRunOncePerAbsentKey() {
    val owner = Owner()
    val calls = AtomicInteger()
    val constructor = Fn2<Value, String, AtomicInteger> { key, count -> count.incrementAndGet(); Value(key) }
    runConcurrent { worker ->
      for (i in 0 until 512) {
        val key = "key_${(i + worker) % 32}"
        val value = owner.attributes.getOrCreate(key, calls, constructor)
        assertSame(value, owner.attributes[key])
        for (j in value.payload.indices) assertEquals(j + 42, value.payload[j])
      }
    }
    assertEquals(32, calls.get())
    assertEquals(32, owner.attributes.size)
  }

  @Test
  fun jvmMapDefaultMethodsKeepTheBackingMapsAtomicity() {
    val map = AttributeMap()
    runConcurrent {
      repeat(256) {
        map.compute("counter") { key, previous -> Counter(key, ((previous as Counter?)?.count ?: 0) + 1) }
      }
    }
    assertEquals(8 * 256, (map["counter"] as Counter).count)
    assertEquals(8 * 256, (map.replace("counter", Counter("counter", 0)) as Counter).count)
    assertSame(map["counter"], map.getOrDefault("counter", Counter("counter", -1)))
  }

  private fun runConcurrent(action: (Int) -> Unit) {
    val executor = Executors.newFixedThreadPool(8)
    val start = CountDownLatch(1)
    try {
      val tasks = ArrayList<Future<Unit>>()
      for (worker in 0 until 8) tasks.add(executor.submit(Callable { start.await(); action(worker) }))
      start.countDown()
      for (task in tasks) task.get(20, TimeUnit.SECONDS)
    } finally {
      executor.shutdownNow()
      assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
    }
  }
}

package naksha.model

import naksha.base.Id
import naksha.base.NakshaException
import naksha.model.objects.NakshaDatabase
import naksha.model.objects.NakshaStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StorageInitializationTest {
  @Test
  fun configIsAvailableDuringInitializationAndAfterInitCanUseStorage() {
    val storage = ProbeStorage()
    val config = NakshaStorage("bootstrap", "ProbeStorage").apply { hardCap = 17 }
    storage.invokeInitStorage(config, null, null)
    assertTrue(storage.initialized)
    assertSame(config, storage.config)
    assertEquals(Id(config.id), storage.id)
    assertEquals(17, storage.hardCap)
    assertEquals(2, storage.hooks)
  }

  @Test
  fun failedInitializationRestoresUninitializedStateAndAllowsRetry() {
    for (afterInit in listOf(false, true)) {
      val storage = ProbeStorage()
      val config = NakshaStorage("bootstrap", "ProbeStorage").apply { hardCap = 17 }
      storage.failureHook = if (afterInit) 2 else 1
      assertFailsWith<IllegalStateException> { storage.invokeInitStorage(config, null, null) }
      assertFalse(storage.initialized)
      assertFailsWith<NakshaException> { storage.config }
      assertFailsWith<NakshaException> { storage.id }
      assertEquals(Int.MAX_VALUE, storage.hardCap)
      storage.failureHook = 0
      storage.invokeInitStorage(config, null, null)
      assertTrue(storage.initialized)
    }
  }

  @Test
  fun failedReinitializationRestoresPreviousConfiguration() {
    for (hook in 1..2) {
      val storage = ProbeStorage()
      val original = NakshaStorage("original", "ProbeStorage").apply { hardCap = 17 }
      storage.invokeInitStorage(original, null, null)
      storage.failureHook = hook
      val replacement = NakshaStorage("replacement", "ProbeStorage").apply { hardCap = 23 }
      assertFailsWith<IllegalStateException> { storage.invokeInitStorage(replacement, true, true) }
      assertTrue(storage.initialized)
      assertSame(original, storage.config)
      assertEquals(Id(original.id), storage.id)
      assertEquals(17, storage.hardCap)
    }
  }

  private class ProbeStorage : AbstractStorage<NakshaStorage>() {
    override val configKlass = NakshaStorage::class
    override val database: NakshaDatabase get() = error("Not used")
    var failureHook = 0
    var hooks = 0

    override fun initStorage(config: NakshaStorage, create: Boolean?, upgrade: Boolean?) {
      assertSame(config, this.config)
      assertEquals(Id(config.id), id)
      assertEquals(config.hardCap, hardCap)
      assertFalse(initialized)
      hooks++
      if (failureHook == 1) error("initStorage failed")
    }

    override fun afterInit() {
      assertTrue(initialized)
      assertSame(this, useInitialized())
      assertEquals(Id(config.id), id)
      hooks++
      if (failureHook == 2) error("afterInit failed")
    }

    override fun shutdownStorage(dropCache: Boolean) = Unit
    override fun newWriteSession(options: SessionOptions?): IWriteSession = error("Not used")
    override fun newReadSession(options: SessionOptions?): IReadSession = error("Not used")
    override fun newStreamSession(options: SessionOptions?): IStreamSession = error("Not used")
  }
}

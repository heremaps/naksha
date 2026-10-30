package naksha.iml

import java.util.UUID
import naksha.base.NakshaError
import naksha.base.NakshaException
import naksha.base.Platform
import naksha.model.Naksha
import naksha.model.objects.NakshaStorage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImlStorageTest {
  @Test
  fun `useStorage reuses equivalent config and replaces changed config`() = ImlTestFixture.withFixture { fixture ->
    val config = fixture.config
    try {
      val first = Naksha.useStorage(config) as ImlStorage
      assertTrue(first.initialized)
      assertEquals(config.id, first.id)
      val equivalent = NakshaStorage.fromJSON(Platform.toJSON(config)).proxy(ImlConfig::class)
      assertSame(first, Naksha.useStorage(equivalent))

      val changed = NakshaStorage.fromJSON(Platform.toJSON(config)).proxy(ImlConfig::class)
      changed.hardCap = config.hardCap + 1
      val replacement = Naksha.useStorage(changed) as ImlStorage
      assertNotSame(first, replacement)
      assertEquals(changed.hardCap, replacement.config.hardCap)
      assertSame(replacement, Naksha.useStorage(changed))
      assertThrows(IllegalStateException::class.java) { first.newReadSession() }
    } finally {
      Naksha.getStorageById(config.id)?.let { Naksha.removeStorage(it.config) }
    }
    assertNull(Naksha.getStorageById(config.id))
  }

  @Test
  fun `invalid JDBC URLs are rejected before registration`() {
    for (url in listOf("", " ", "postgresql://localhost/iml", "jdbc:mysql://localhost/iml")) {
      val config = config().withJdbcUrl(url)
      try {
        val error = assertThrows(NakshaException::class.java) { Naksha.useStorage(config) }
        assertEquals(NakshaError.ILLEGAL_ARGUMENT, error.error.code)
        assertNull(Naksha.getStorageById(config.id))
      } finally {
        Naksha.removeStorage(config)
      }
    }
  }

  @Test
  fun `create and upgrade flags are rejected`() {
    for ((create, upgrade) in listOf(true to false, false to true, true to true)) {
      val config = config()
      config.create = create
      config.upgrade = upgrade
      try {
        val error = assertThrows(NakshaException::class.java) { Naksha.useStorage(config) }
        assertEquals(NakshaError.UNSUPPORTED_OPERATION, error.error.code)
        assertNull(Naksha.getStorageById(config.id))
      } finally {
        Naksha.removeStorage(config)
      }
    }
  }

  @Test
  fun `setupStorage cannot force creation or upgrade of an existing storage`() = ImlTestFixture.withFixture { fixture ->
    val config = fixture.config
    try {
      val storage = Naksha.useStorage(config)
      val error = assertThrows(NakshaException::class.java) { Naksha.setupStorage(config) }
      assertEquals(NakshaError.UNSUPPORTED_OPERATION, error.error.code)
      assertSame(storage, Naksha.useStorage(config))
    } finally {
      Naksha.removeStorage(config)
    }
  }

  @Test
  fun `write and stream sessions are rejected`() {
    val storage = ImlStorage()
    val writeError = assertThrows(NakshaException::class.java) { storage.newWriteSession() }
    val streamError = assertThrows(NakshaException::class.java) { storage.newStreamSession() }
    assertEquals(NakshaError.UNSUPPORTED_OPERATION, writeError.error.code)
    assertEquals(NakshaError.UNSUPPORTED_OPERATION, streamError.error.code)
  }

  @Test
  fun `uninitialized storage rejects reads and configuration access`() {
    val storage = ImlStorage()
    assertFalse(storage.initialized)
    val actions = listOf<() -> Any?>(
      { storage.newReadSession() },
      { storage.config },
      { storage.id },
      { storage.number }
    )
    for (action in actions) {
      val error = assertThrows(NakshaException::class.java) { action() }
      assertEquals(NakshaError.UNINITIALIZED, error.error.code)
    }
  }

  private fun config() = ImlConfig("iml-unit-${UUID.randomUUID()}", "jdbc:postgresql://localhost/not_connected")
}

package naksha.iml

import naksha.base.Platform
import naksha.model.objects.NakshaStorage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImlConfigTest {
  @Test
  fun `defaults and fluent setters use proxy properties`() {
    val config = ImlConfig()
    assertEquals("", config.jdbcUrl)
    assertEquals(ImlStorage::class.java.name, config.className)
    assertFalse(config.create)
    assertFalse(config.upgrade)
    assertSame(config, config.withId("iml-config"))
    assertSame(config, config.withJdbcUrl("jdbc:postgresql://localhost/iml"))
    assertEquals("iml-config", config.id)
    assertEquals("jdbc:postgresql://localhost/iml", config.jdbcUrl)
  }

  @Test
  fun `configuration survives JSON parsing and typed proxying`() {
    val config = ImlConfig("iml-round-trip", "jdbc:postgresql://localhost/iml?sslmode=require")
    config.hardCap = 1234
    val parsed = NakshaStorage.fromJSON(Platform.toJSON(config))
    val restored = parsed.proxy(ImlConfig::class)

    assertEquals(config.id, restored.id)
    assertEquals(config.className, restored.className)
    assertEquals(config.jdbcUrl, restored.jdbcUrl)
    assertEquals(config.hardCap, restored.hardCap)
    assertFalse(restored.create)
    assertFalse(restored.upgrade)
    assertTrue(config.configEquals(restored))

    restored.withJdbcUrl("jdbc:postgresql://localhost/changed")
    assertEquals(restored.jdbcUrl, parsed.proxy(ImlConfig::class).jdbcUrl)
    assertEquals("jdbc:postgresql://localhost/iml?sslmode=require", config.jdbcUrl)
  }
}

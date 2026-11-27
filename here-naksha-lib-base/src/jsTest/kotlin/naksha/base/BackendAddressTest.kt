package naksha.base

import kotlin.test.Test
import kotlin.test.assertEquals

class BackendAddressTest {
  @Test
  fun internalWorkersAndSocketSessionsUseANonNullApplicationIdentity() {
    val original = js("globalThis.plv8")
    val native = js("({})")
    js("globalThis.plv8 = native")
    try {
      native.execute = { _: String -> js("[{ip:null}]") }
      assertEquals("plv8", Platform.primaryLocalIp())
      native.execute = { _: String -> js("[]") }
      assertEquals("plv8", Platform.primaryLocalIp())
      native.execute = { _: String -> js("[{ip:'127.0.0.1'}]") }
      assertEquals("127.0.0.1", Platform.primaryLocalIp())
    } finally {
      js("globalThis.plv8 = original")
    }
  }
}

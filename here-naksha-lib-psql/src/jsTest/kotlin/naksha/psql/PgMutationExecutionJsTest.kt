package naksha.psql

import naksha.base.NakshaError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PgMutationExecutionJsTest {
  @Test
  fun mutationPlansUseExecuteRatherThanOpeningACursor() {
    var executes = 0
    var frees = 0
    val native = js("({})")
    val args = arrayOf<Any?>(arrayOf(Long.MIN_VALUE, Long.MAX_VALUE))
    native.execute = { received: dynamic ->
      executes++
      assertEquals(Long.MIN_VALUE.toString(), received[0][0].toString())
      assertEquals(Long.MAX_VALUE.toString(), received[0][1].toString())
      js("[{version: '9007199254740991'}]")
    }
    native.cursor = { throw IllegalStateException("A returning mutation must not open a read cursor") }
    native.free = { frees++ }
    PgPlan(native.unsafeCast<PreparedPlan>()).use { plan ->
      plan.executeMutation(args).use { result ->
        assertTrue(result.next())
        assertEquals("9007199254740991", result.get<String>("version"))
        assertFalse(result.next())
      }
    }
    assertEquals(1, executes)
    assertEquals(1, frees)
  }

  @Test
  fun nativeSqlStatesAndWrappedErrorsArePreserved() {
    val native = js("new Error('deadlock detected')").unsafeCast<Throwable>()
    native.asDynamic().sqlerrcode = "40P01"
    val mapped = PgExceptionMapper.map(native)
    assertEquals(NakshaError.CONFLICT, mapped.error.code)
    assertSame(native, mapped.cause)
    assertEquals("40P01", PgExceptionMapper.sqlState(mapped))
    assertSame(mapped, PgExceptionMapper.map(mapped))
  }
}

package naksha.jbon

import naksha.base.NakshaException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class JbMemberReferenceInputTest {
  @Test
  fun javascriptCallersCannotTruncateFractionalOrNonFiniteIndexes() {
    val builder = JbMemberReferenceBuilder().asDynamic()
    assertFailsWith<NakshaException> { builder.add(js("['value']"), 0.5) }
    assertFailsWith<NakshaException> { builder.add(js("['value']"), js("NaN")) }
    assertFailsWith<NakshaException> { builder.add(js("['value']"), js("Infinity")) }
    assertFailsWith<NakshaException> { builder.add(js("['value']"), 2147483648.0) }
    assertFailsWith<NakshaException> { builder.add(js("['value']"), "1") }
    assertFailsWith<NakshaException> { builder.add(js("['value']"), null) }
    assertFailsWith<NakshaException> { builder.add(js("['value']"), js("BigInt(1)")) }
  }
}

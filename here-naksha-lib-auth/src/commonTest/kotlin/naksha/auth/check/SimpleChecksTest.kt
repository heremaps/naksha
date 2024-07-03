package naksha.auth.check

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimpleChecksTest {

    @Test
    fun equalsShouldPassOnEqualObjects() {
        assertTrue {
            EqualsCheck("hey").matches("hey")
        }
    }

    @Test
    fun equalsShouldFailOnDifferentObjects() {
        assertFalse {
            EqualsCheck("foo").matches("bar")
        }
    }

    @Test
    fun endsWithShouldPass() {
        assertTrue {
            EndsWithCheck("ipsum").matches("Lorem ipsum")
        }
    }

    @Test
    fun endsWithShouldFail() {
        assertFalse {
            EndsWithCheck("Lorem").matches("Lorem ipsum")
        }
    }


    @Test
    fun startsWithShouldPass() {
        assertTrue {
            StartsWithCheck("Lorem").matches("Lorem ipsum")
        }
    }

    @Test
    fun startsWithShouldFail() {
        assertFalse {
            StartsWithCheck("ipsum").matches("Lorem ipsum")
        }
    }
}
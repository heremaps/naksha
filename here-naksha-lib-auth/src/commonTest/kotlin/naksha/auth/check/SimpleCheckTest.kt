package naksha.auth.check

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimpleCheckTest {

    @Test
    fun equalsShouldPassOnEqualObjects() {
        assertTrue {
            Equals("hey").matches("hey")
        }
    }

    @Test
    fun equalsShouldFailOnDifferentObjects() {
        assertFalse {
            Equals("foo").matches("bar")
        }
    }

    @Test
    fun endsWithShouldPass() {
        assertTrue {
            EndsWith("ipsum").matches("Lorem ipsum")
        }
    }

    @Test
    fun endsWithShouldFail() {
        assertFalse {
            EndsWith("Lorem").matches("Lorem ipsum")
        }
    }


    @Test
    fun startsWithShouldPass() {
        assertTrue {
            StartsWith("Lorem").matches("Lorem ipsum")
        }
    }

    @Test
    fun startsWithShouldFail() {
        assertFalse {
            StartsWith("ipsum").matches("Lorem ipsum")
        }
    }
}
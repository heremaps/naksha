package naksha.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NakshaVersionTest {

    @Test
    fun sourceShouldBeEqualTarget() {
        val source = NakshaVersion.of("3.0.0-beta.19")
        val target = NakshaVersion.of("3.0.0-beta.19")
        assertEquals(source, target)
    }

    @Test
    fun sourceShouldBeLessThanTarget() {
        var source = NakshaVersion.of("3.0.0-beta.19")
        var target = NakshaVersion.of("3.0.0-beta.20")
        assertTrue(source < target, "Expected $source to be less than $target")

        source = NakshaVersion.of("3.0.0-beta.16")
        target = NakshaVersion.of("3.0.0-beta.17")
        assertTrue(source < target, "Expected $source to be less than $target")

        source = NakshaVersion.of("3.0.0-alpha.21")
        target = NakshaVersion.of("3.0.0-beta.20")
        assertTrue(source < target,"Expected $source to be less than $target")

        source = NakshaVersion.of("2.9.99")
        target = NakshaVersion.of("3.0.0-beta.20")
        assertTrue(source < target,"Expected $source to be less than $target")
    }

    @Test
    fun sourceShouldBeGreaterThanTarget() {
        var source = NakshaVersion.of("3.0.0-beta.20")
        var target = NakshaVersion.of("3.0.0-beta.19")
        assertTrue(source > target, "Expected $source to be greater than $target")

        source = NakshaVersion.of("3.0.0-beta.20")
        target = NakshaVersion.of("3.0.0-alpha.21")
        assertTrue(source > target,"Expected $source to be greater than $target")

        source = NakshaVersion.of("3.0.0-beta.20")
        target = NakshaVersion.of("2.9.99")
        assertTrue(source > target,"Expected $source to be greater than $target")
    }
}
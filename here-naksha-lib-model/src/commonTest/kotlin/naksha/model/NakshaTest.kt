package naksha.model

import naksha.base.PlatformUtil.PlatformUtilCompanion.randomString
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NakshaTest {

    @Test
    fun shouldLimitCollectionIdLength() {
        // expect
        var collectionId = collectionIdOf(1)
        assertTrue(collectionId) { Naksha.isValidId(collectionId) }
        collectionId = collectionIdOf(45)
        assertTrue(collectionId) { Naksha.isValidId(collectionId) }

        collectionId = collectionIdOf(46)
        assertFalse(collectionId) { Naksha.isValidId(collectionId) }
        assertFalse(collectionId) { Naksha.isValidId("") }
    }

    @Test
    fun shouldOnlyAllowCharacterAsFirstChar() {
        // expect
        assertTrue{ Naksha.isValidId("c1232_name") }
        assertFalse{ Naksha.isValidId("11232_name") }
    }

    @Test
    fun shouldNotAllowCapitalLettersOrUnsupportedCharacters() {
        // expect
        assertFalse{ Naksha.isValidId("C1232_name") }
        assertFalse{ Naksha.isValidId("name\$a") }
        assertFalse{ Naksha.isValidId("name&a") }
        assertFalse{ Naksha.isValidId("name*a") }
        assertFalse{ Naksha.isValidId("name#a") }
        assertFalse{ Naksha.isValidId("name@a") }
        assertFalse{ Naksha.isValidId("name!a") }

        assertTrue{ Naksha.isValidId("name_a") }
        assertTrue{ Naksha.isValidId("name-a") }
        assertTrue{ Naksha.isValidId("name:a") }
    }

    private fun collectionIdOf(length: Int): String = "c" + randomString(length - 1).lowercase()
}
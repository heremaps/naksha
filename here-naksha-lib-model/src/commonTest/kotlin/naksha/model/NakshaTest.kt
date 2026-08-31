package naksha.model

import naksha.base.Binary
import naksha.base.Platform
import naksha.base.PlatformUtil.PlatformUtilCompanion.randomString
import naksha.model.Naksha.NakshaCompanion.INT64_SIGN_BIT
import naksha.model.NakshaIdType
import kotlin.test.*

class NakshaTest {

    @Test
    fun shouldLimitCollectionIdLength() {
        // expect
        var collectionId = collectionIdOf(1)
        assertTrue(collectionId) { NakshaIdType.COLLECTION.isValidId(collectionId) }
        collectionId = collectionIdOf(42)
        assertTrue(collectionId) { NakshaIdType.COLLECTION.isValidId(collectionId) }

        collectionId = collectionIdOf(45)
        assertFalse(collectionId) { NakshaIdType.COLLECTION.isValidId(collectionId) }
        assertFalse(collectionId) { NakshaIdType.COLLECTION.isValidId("") }
    }

    @Test
    fun shouldOnlyAllowCharacterAsFirstChar() {
        // expect
        assertTrue{ NakshaIdType.COLLECTION.isValidId("c1232_name") }
        assertFalse{ NakshaIdType.COLLECTION.isValidId("11232_name") }

        assertTrue{ NakshaIdType.CATALOG.isValidId("c1232_name") }
        assertFalse{ NakshaIdType.CATALOG.isValidId("11232_name") }
    }

    @Test
    fun shouldConvertNumericIdsToFeatureNumber() {
        // expect
        assertEquals(0L, Naksha.featureNumber("0"))
        assertEquals(1L, Naksha.featureNumber("1"))
        assertEquals(5000L, Naksha.featureNumber("5000"))
        assertEquals(9223372036854775807L, Naksha.featureNumber("9223372036854775807"))
    }

    @Test
    fun shouldNotConvertNegativeNumericIdsToFeatureNumber() {
        // expect
        assertNotEquals(-1L, Naksha.featureNumber("-1"))
        assertNotEquals(-100L, Naksha.featureNumber("-100"))
    }

    @Test
    fun shouldNotConvertNumericIdsWithLeadingZeroToFeatureNumber() {
        // expect
        assertNotEquals(0L, Naksha.featureNumber("00"))
        assertNotEquals(1L, Naksha.featureNumber("01"))
    }

    @Test
    fun shouldNotAllowCapitalLettersOrUnsupportedCharacters() {
        // expect
        assertFalse{ NakshaIdType.COLLECTION.isValidId("C1232_name") }
        assertFalse{ NakshaIdType.COLLECTION.isValidId("name\$a") }
        assertFalse{ NakshaIdType.COLLECTION.isValidId("name&a") }
        assertFalse{ NakshaIdType.COLLECTION.isValidId("name*a") }
        assertFalse{ NakshaIdType.COLLECTION.isValidId("name#a") }
        assertFalse{ NakshaIdType.COLLECTION.isValidId("name@a") }
        assertFalse{ NakshaIdType.COLLECTION.isValidId("name!a") }

        assertTrue{ NakshaIdType.COLLECTION.isValidId("name_a") }
        assertTrue{ NakshaIdType.COLLECTION.isValidId("name-a") }
        assertTrue{ NakshaIdType.COLLECTION.isValidId("name:a") }

        assertFalse{ NakshaIdType.CATALOG.isValidId("C1232_name") }
        assertFalse{ NakshaIdType.CATALOG.isValidId("name\$a") }
        assertFalse{ NakshaIdType.CATALOG.isValidId("name&a") }
        assertFalse{ NakshaIdType.CATALOG.isValidId("name*a") }
        assertFalse{ NakshaIdType.CATALOG.isValidId("name#a") }
        assertFalse{ NakshaIdType.CATALOG.isValidId("name@a") }
        assertFalse{ NakshaIdType.CATALOG.isValidId("name!a") }

        assertTrue{ NakshaIdType.CATALOG.isValidId("name_a") }
        assertTrue{ NakshaIdType.CATALOG.isValidId("name-a") }
        assertTrue{ NakshaIdType.CATALOG.isValidId("name:a") }
    }

    private fun collectionIdOf(length: Int): String = "c" + randomString(length - 1).lowercase()

    companion object NakshaTest_C {
        /**
         * A map between a feature-id and its feature-number, partition-number.
         */
        val featureNumbers = mapOf(
            Pair("apple", Pair(-5484511280634489473L, 38271)),
            Pair("mountain", Pair(-7643729615383602773L, 22955)),
            Pair("river", Pair(-8583638578235004148L, 43788)),
            Pair("sunshine", Pair(-4226617501311062128L, 44944)),
            Pair("thunderstorm", Pair(-1662902383593395660L, 59956)),
            Pair("ocean", Pair(-814082869712769487L, 11825)),
            Pair("whisper", Pair(-3749626363697139386L, 28998)),
            Pair("horizon", Pair(-7642222644562628650L, 61398)),
            Pair("galaxy", Pair(-883295718727625417L, 54583)),
            Pair("waterfall", Pair(-7660642750086972487L, 64441)),
            Pair("adventure", Pair(-7128644326194761764L, 45020)),
            Pair("brilliant", Pair(-5335221019134199366L, 63930)),
            Pair("cinnamon", Pair(-2248730746238684729L, 45511)),
            Pair("discover", Pair(-6128714515268687570L, 10542)),
            Pair("elephant", Pair(-7157438305210013553L, 59535)),
            Pair("friendship", Pair(-6290951814662559583L, 26785)),
            Pair("grateful", Pair(-700925958107943057L, 12143)),
            Pair("happiness", Pair(-4386989945280691918L, 33074)),
            Pair("inspire", Pair(-2792765718126701116L, 21956)),
            Pair("journey", Pair(-5734938501861003441L, 12111)),
            Pair("kindness", Pair(-432081468076469306L, 54214)),
            Pair("lighthouse", Pair(-2138142157099628800L, 1792)),
            Pair("marvelous", Pair(-4532173556937446518L, 11146)),
            Pair("nostalgia", Pair(-8803792262422751274L, 38870)),
            Pair("optimistic", Pair(-5230136328746208797L, 29155)),
            Pair("peaceful", Pair(-5292427495373678802L, 55086)),
            Pair("question", Pair(-5073713729616654471L, 20345)),
            Pair("remarkable", Pair(-3533350706736017749L, 52907)),
            Pair("timeless", Pair(-8277156549585841745L, 20911)),
            Pair("universe", Pair(-1692936746790842146L, 37086)),
            Pair("victorious", Pair(-6472092707646497588L, 40140)),
            Pair("wonderful", Pair(-284636615007774547L, 22701)),
            Pair("zephyr", Pair(-6766911163183497362L, 32622)),
            Pair("A7l9RsIxWZCp2I6i3wXo", Pair(-6293233423437375615L, 22401)),
            Pair("A6ixOLtAZF8IhKez25zY", Pair(-318328739946057960L, 44824)),
        )
    }

    private fun toHex(bytes: ByteArray): String = buildString {
        for (byte in bytes) {
            append(byte.toUByte().toString(16).padStart(2, '0'))
        }
    }
    private fun printPairIfIncorrect(id: String, fn: Long, pn: Int) {
        val bytes = Platform.md5(id)
        // To compare: https://www.md5hashgenerator.com/
        val hex = toHex(bytes)
        val view = Binary(bytes)
        val lower = view.getInt64(8)
        val signedLower = lower or INT64_SIGN_BIT
        val lower16 = signedLower.toInt() and 65535
        if (signedLower != fn || lower16 != pn) {
            println("Pair(\"$id\", Pair(${signedLower}L, ${lower16})),")
        }
    }
    private fun generate() {
        for (entry in featureNumbers) {
            val id = entry.key
            val (fn, pn) = entry.value
            printPairIfIncorrect(id, fn, pn)
        }

    }

    @Test
    fun featureNumberTest() {
        generate()
        for (entry in featureNumbers) {
            val id = entry.key
            val (fn, pn) = entry.value
            val c_fn = Naksha.featureNumber(id)
            assertEquals(fn, c_fn, "Expected that feature-number of '$id' is '$fn', but was '$c_fn'")
            val c_pn = Naksha.partitionNumber(c_fn)
            assertEquals(pn, c_pn, "Expected that partition of '$id' is '$pn', but was '$c_pn'")
        }
    }
}

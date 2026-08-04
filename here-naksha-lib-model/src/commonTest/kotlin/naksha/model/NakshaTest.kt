package naksha.model

import naksha.base.Binary
import naksha.base.FeatureType
import naksha.base.Int64
import naksha.base.Base
import naksha.base.BaseUtil.BaseUtil_C.randomAtoZ
import naksha.model.Naksha.NakshaCompanion.INT64_SIGN_BIT
import kotlin.test.*

class NakshaTest {

    @Test
    fun shouldLimitCollectionIdLength() {
        // expect
        var collectionId = collectionIdOf(1)
        assertTrue(collectionId) { FeatureType.COLLECTION.isValidId(collectionId) }
        collectionId = collectionIdOf(42)
        assertTrue(collectionId) { FeatureType.COLLECTION.isValidId(collectionId) }

        collectionId = collectionIdOf(45)
        assertFalse(collectionId) { FeatureType.COLLECTION.isValidId(collectionId) }
        assertFalse(collectionId) { FeatureType.COLLECTION.isValidId("") }
    }

    @Test
    fun shouldOnlyAllowCharacterAsFirstChar() {
        // expect
        assertTrue{ FeatureType.COLLECTION.isValidId("c1232_name") }
        assertFalse{ FeatureType.COLLECTION.isValidId("11232_name") }

        assertTrue{ FeatureType.CATALOG.isValidId("c1232_name") }
        assertFalse{ FeatureType.CATALOG.isValidId("11232_name") }
    }

    @Test
    fun shouldConvertNumericIdsToFeatureNumber() {
        // expect
        assertEquals(Int64(0L), Naksha.featureNumber("0"))
        assertEquals(Int64(1L), Naksha.featureNumber("1"))
        assertEquals(Int64(5000L), Naksha.featureNumber("5000"))
        assertEquals(Int64(9223372036854775807L), Naksha.featureNumber("9223372036854775807"))
    }

    @Test
    fun shouldNotConvertNegativeNumericIdsToFeatureNumber() {
        // expect
        assertNotEquals(Int64(-1L), Naksha.featureNumber("-1"))
        assertNotEquals(Int64(-100L), Naksha.featureNumber("-100"))
    }

    @Test
    fun shouldNotConvertNumericIdsWithLeadingZeroToFeatureNumber() {
        // expect
        assertNotEquals(Int64(0L), Naksha.featureNumber("00"))
        assertNotEquals(Int64(1L), Naksha.featureNumber("01"))
    }

    @Test
    fun shouldNotAllowCapitalLettersOrUnsupportedCharacters() {
        // expect
        assertFalse{ FeatureType.COLLECTION.isValidId("C1232_name") }
        assertFalse{ FeatureType.COLLECTION.isValidId("name\$a") }
        assertFalse{ FeatureType.COLLECTION.isValidId("name&a") }
        assertFalse{ FeatureType.COLLECTION.isValidId("name*a") }
        assertFalse{ FeatureType.COLLECTION.isValidId("name#a") }
        assertFalse{ FeatureType.COLLECTION.isValidId("name@a") }
        assertFalse{ FeatureType.COLLECTION.isValidId("name!a") }

        assertTrue{ FeatureType.COLLECTION.isValidId("name_a") }
        assertTrue{ FeatureType.COLLECTION.isValidId("name-a") }
        assertTrue{ FeatureType.COLLECTION.isValidId("name:a") }

        assertFalse{ FeatureType.CATALOG.isValidId("C1232_name") }
        assertFalse{ FeatureType.CATALOG.isValidId("name\$a") }
        assertFalse{ FeatureType.CATALOG.isValidId("name&a") }
        assertFalse{ FeatureType.CATALOG.isValidId("name*a") }
        assertFalse{ FeatureType.CATALOG.isValidId("name#a") }
        assertFalse{ FeatureType.CATALOG.isValidId("name@a") }
        assertFalse{ FeatureType.CATALOG.isValidId("name!a") }

        assertTrue{ FeatureType.CATALOG.isValidId("name_a") }
        assertTrue{ FeatureType.CATALOG.isValidId("name-a") }
        assertTrue{ FeatureType.CATALOG.isValidId("name:a") }
    }

    private fun collectionIdOf(length: Int): String = "c" + randomAtoZ(length - 1).lowercase()

    companion object NakshaTest_C {
        /**
         * A map between a feature-id and its feature-number, partition-number.
         */
        val featureNumbers = mapOf(
            Pair("apple", Pair(Int64(-5484511280634489473), 38271)),
            Pair("mountain", Pair(Int64(-7643729615383602773), 22955)),
            Pair("river", Pair(Int64(-8583638578235004148), 43788)),
            Pair("sunshine", Pair(Int64(-4226617501311062128), 44944)),
            Pair("thunderstorm", Pair(Int64(-1662902383593395660), 59956)),
            Pair("ocean", Pair(Int64(-814082869712769487), 11825)),
            Pair("whisper", Pair(Int64(-3749626363697139386), 28998)),
            Pair("horizon", Pair(Int64(-7642222644562628650), 61398)),
            Pair("galaxy", Pair(Int64(-883295718727625417), 54583)),
            Pair("waterfall", Pair(Int64(-7660642750086972487), 64441)),
            Pair("adventure", Pair(Int64(-7128644326194761764), 45020)),
            Pair("brilliant", Pair(Int64(-5335221019134199366), 63930)),
            Pair("cinnamon", Pair(Int64(-2248730746238684729), 45511)),
            Pair("discover", Pair(Int64(-6128714515268687570), 10542)),
            Pair("elephant", Pair(Int64(-7157438305210013553), 59535)),
            Pair("friendship", Pair(Int64(-6290951814662559583), 26785)),
            Pair("grateful", Pair(Int64(-700925958107943057), 12143)),
            Pair("happiness", Pair(Int64(-4386989945280691918), 33074)),
            Pair("inspire", Pair(Int64(-2792765718126701116), 21956)),
            Pair("journey", Pair(Int64(-5734938501861003441), 12111)),
            Pair("kindness", Pair(Int64(-432081468076469306), 54214)),
            Pair("lighthouse", Pair(Int64(-2138142157099628800), 1792)),
            Pair("marvelous", Pair(Int64(-4532173556937446518), 11146)),
            Pair("nostalgia", Pair(Int64(-8803792262422751274), 38870)),
            Pair("optimistic", Pair(Int64(-5230136328746208797), 29155)),
            Pair("peaceful", Pair(Int64(-5292427495373678802), 55086)),
            Pair("question", Pair(Int64(-5073713729616654471), 20345)),
            Pair("remarkable", Pair(Int64(-3533350706736017749), 52907)),
            Pair("timeless", Pair(Int64(-8277156549585841745), 20911)),
            Pair("universe", Pair(Int64(-1692936746790842146), 37086)),
            Pair("victorious", Pair(Int64(-6472092707646497588), 40140)),
            Pair("wonderful", Pair(Int64(-284636615007774547), 22701)),
            Pair("zephyr", Pair(Int64(-6766911163183497362), 32622)),
            Pair("A7l9RsIxWZCp2I6i3wXo", Pair(Int64(-6293233423437375615), 22401)),
            Pair("A6ixOLtAZF8IhKez25zY", Pair(Int64(-318328739946057960), 44824)),
        )
    }

    private fun toHex(bytes: ByteArray): String = buildString {
        for (byte in bytes) {
            append(byte.toUByte().toString(16).padStart(2, '0'))
        }
    }
    private fun printPairIfIncorrect(id: String, fn: Int64, pn: Int) {
        val bytes = Base.md5(id)
        // To compare: https://www.md5hashgenerator.com/
        val hex = toHex(bytes)
        val view = Binary(bytes)
        val lower = view.getInt64(8)
        val signedLower = lower or INT64_SIGN_BIT
        val lower16 = signedLower.toInt() and 65535
        if (signedLower != fn || lower16 != pn) {
            println("Pair(\"$id\", Pair(Int64($signedLower), ${lower16})),")
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

package naksha.base

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IdTextTypeTest {
    @Test
    fun classifiesOrdinaryInternalAndReservedNames() {
        val examples = listOf(
            "roads" to IdTextType.ALPHABET_ONLY,
            "Roads" to IdTextType.URL_SAFE,
            "A_1" to IdTextType.URL_SAFE,
            "a0" to IdTextType.SAFE,
            "_roads" to IdTextType.SAFE,
            "0roads" to IdTextType.URL_SAFE,
            "roads-west.v1~old" to IdTextType.URL_SAFE,
            "a b" to IdTextType.UNSAFE,
            "a/b?c" to IdTextType.UNSAFE,
            "a\\b" to IdTextType.UNSAFE,
            "caf\u00e9" to IdTextType.UNSAFE,
            "\ud83d\ude80" to IdTextType.UNSAFE,
            "m\$12345" to IdTextType.INTERNAL,
            "m\$_12345" to IdTextType.INTERNAL,
            "a\$" to IdTextType.INTERNAL,
            "naksha~admin" to IdTextType.RESERVED,
            "naksha~${"a".repeat(25)}" to IdTextType.RESERVED,
            "naksha~" to IdTextType.ILLEGAL,
            "naksha~${"a".repeat(26)}" to IdTextType.ILLEGAL,
            "naksha~Admin" to IdTextType.ILLEGAL,
            "naksha~a-b" to IdTextType.ILLEGAL,
            "naksha~a\$b" to IdTextType.ILLEGAL,
            "M\$1" to IdTextType.ILLEGAL,
            "\$1" to IdTextType.ILLEGAL,
            "1m\$1" to IdTextType.ILLEGAL,
            "m-\$1" to IdTextType.ILLEGAL,
            "" to IdTextType.ILLEGAL,
            "a'b" to IdTextType.ILLEGAL,
            "a\"b" to IdTextType.ILLEGAL,
            "a\ud800" to IdTextType.ILLEGAL,
            "\udc00a" to IdTextType.ILLEGAL,
            "\ud800a" to IdTextType.ILLEGAL
        )
        for ((text, expected) in examples) {
            assertEquals(expected, IdTextType.classify(text), text)
            assertEquals(expected, Id(text).textType, text)
        }
        assertEquals(IdTextType.ALPHABET_ONLY, Id("a".repeat(1000)).textType)
        assertEquals(IdTextType.INTERNAL, Id("m\$${"1".repeat(1000)}").textType)
    }

    @Test
    fun rejectsEveryControlCharacterAndIllegalAliasRequest() {
        for (code in 0..0x9f) {
            if (code in 0x20..0x7e) continue
            val text = "a${code.toChar()}b"
            assertEquals(IdTextType.ILLEGAL, IdTextType.classify(text))
            val error = assertFailsWith<NakshaException> { Id(text).textAlias(IdTextType.RESERVED, 100, "m") }
            assertEquals(NakshaError.ILLEGAL_ID, error.error.code)
        }
        for (text in listOf("", "a'b", "a\"b", "A\$1", "naksha~", "a\ud800")) {
            val error = assertFailsWith<NakshaException> { Id(text).textAlias(IdTextType.RESERVED, 100, "m") }
            assertEquals(NakshaError.ILLEGAL_ID, error.error.code)
        }
    }

    @Test
    fun acceptanceIsCumulativeButNeverAcceptsIllegalText() {
        val legal = listOf(IdTextType.ALPHABET_ONLY, IdTextType.SAFE, IdTextType.URL_SAFE,
            IdTextType.UNSAFE, IdTextType.INTERNAL, IdTextType.RESERVED)
        for (i in legal.indices) {
            assertEquals(i, legal[i].intValue)
            for (j in legal.indices) assertEquals(j <= i, legal[i].accepts(legal[j]))
            assertFalse(legal[i].accepts(IdTextType.ILLEGAL))
            assertFalse(IdTextType.ILLEGAL.accepts(legal[i]))
            assertEquals(legal[i], JsEnum.get(i, IdTextType::class))
        }
        assertEquals(127, IdTextType.ILLEGAL.intValue)
        assertFalse(IdTextType.ILLEGAL.accepts(IdTextType.ILLEGAL))
    }

    @Test
    fun aliasesOnlyWhenTypeOrUtf8SizeDoesNotFit() {
        assertEquals("roads", Id("roads").textAlias(IdTextType.ALPHABET_ONLY, 5, "m"))
        assertEquals("Roads", Id("Roads").textAlias(IdTextType.URL_SAFE, 5, "m"))
        assertEquals("a_1", Id("a_1").textAlias(IdTextType.SAFE, 3, "m"))
        assertEquals("a~b", Id("a~b").textAlias(IdTextType.URL_SAFE, 3, "m"))
        assertEquals("\u00e9", Id("\u00e9").textAlias(IdTextType.UNSAFE, 2, "m"))
        assertEquals("\ud83d\ude80", Id("\ud83d\ude80").textAlias(IdTextType.UNSAFE, 4, "m"))
        for ((text, expected, maxBytes) in listOf(
            Triple("Roads", IdTextType.SAFE, 4),
            Triple("Roads", IdTextType.ALPHABET_ONLY, 100),
            Triple("Roads", IdTextType.SAFE, 100),
            Triple("a_1", IdTextType.ALPHABET_ONLY, 100),
            Triple("a~b", IdTextType.SAFE, 100),
            Triple("\u00e9", IdTextType.UNSAFE, 1),
            Triple("\ud83d\ude80", IdTextType.UNSAFE, 3),
            Triple("m\$1", IdTextType.SAFE, 100),
            Triple("m\$1", IdTextType.INTERNAL, 2),
            Triple("naksha~admin", IdTextType.INTERNAL, 100),
            Triple("naksha~admin", IdTextType.RESERVED, 11)
        )) {
            val id = Id(text)
            assertEquals("m\$${id.intValue.toString().replace('-', '_')}", id.textAlias(expected, maxBytes, "m"))
        }
        assertEquals("m\$1", Id("m\$1").textAlias(IdTextType.INTERNAL, 3, "m"))
        assertEquals("naksha~admin", Id("naksha~admin").textAlias(IdTextType.RESERVED, 12, "m"))
    }

    @Test
    fun fallbackDoesNotValidatePrefixOrEnforceByteBudget() {
        val negative = Id("long name", -12345L, skipVerification = true)
        val positive = Id("long name", 12345L, skipVerification = true)
        assertEquals("m\$_12345", negative.textAlias(IdTextType.SAFE, 0, "m"))
        assertEquals("m\$12345", positive.textAlias(IdTextType.SAFE, 0, "m"))
        assertEquals("odd prefix\$_12345", negative.textAlias(IdTextType.SAFE, -1, "odd prefix"))
        assertTrue(negative.textAlias(IdTextType.SAFE, 0, "m").length > 0)
    }
}

package naksha.jbon

import naksha.base.PlatformUtil.PlatformUtilCompanion.hashCodeOf
import naksha.base.NakshaException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MemberValuesBookTest {
  @Test
  fun fixedSlotsNamesStringsAndNullsFollowBookContract() {
    val names = arrayOf("first", "missing", "last")
    val layout = MemberBookLayout(names)
    names[0] = "changed"
    val book = MemberValuesBook(layout)
    book.setValue(0, "value")
    book.setValue(2, 42)
    assertEquals(3, book.length)
    assertEquals(3, book.namesLength())
    assertTrue(book.hasNames())
    assertEquals(BookType.MEMBER_BOOK, book.bookType)
    assertEquals(0, book.indexOfName("first"))
    assertEquals(-1, book.indexOfName("changed"))
    assertEquals(2, book.indexOfString("last"))
    assertEquals("first", book.getStringAt(0))
    assertEquals("value", book["first"])
    assertEquals("missing", book.getNameAt(1))
    assertNull(book[1])
    assertNull(book[-1])
    assertNull(book[3])
    assertNull(book["absent"])
    assertNull(book.getNameAt(-1))
    assertNull(book.getStringAt(3))
    assertFailsWith<NakshaException> { book.setValue(3, "invalid") }
    assertFailsWith<NakshaException> { book.setNamedValue("absent", 1) }
    assertFailsWith<NakshaException> { MemberBookLayout(arrayOf("duplicate", "duplicate")) }
  }

  @Test
  fun copiesOwnValuesAndPreserveMetadataAndCollidingHashEntries() {
    val layout = MemberBookLayout(arrayOf("a", "b", "null"))
    val book = MemberValuesBook(layout)
    book.id = "book"
    book.databaseNumber = 10L
    book.featureNumber = 20L
    book.setValue(0, "Aa")
    book.setValue(1, "BB")
    val copy = MemberValuesBook.copyOf(book)
    assertSame(layout, copy.layout)
    assertEquals("book", copy.id)
    assertEquals(10L, copy.databaseNumber)
    assertEquals(20L, copy.featureNumber)
    copy.setValue(0, "changed")
    assertEquals("Aa", book[0])
    val matches = book.getAllWithHash(hashCodeOf("Aa"))
    assertEquals(listOf(0, 1), matches.map { it.index })
    for (entry in matches) assertSame(book, entry.dict)
    assertEquals(listOf(2), book.getAllWithHash(hashCodeOf(null)).map { it.index })
    val other = MemberValuesBook(layout)
    other.setValue(1, 99)
    assertEquals("BB", book[1])
    val heap = HeapBook(BookType.MEMBER_BOOK).apply { put("a", null); put("b", 2); id = "legacy" }
    val converted = MemberValuesBook.copyOf(heap)
    assertEquals(2, converted.length)
    assertNull(converted[0])
    assertEquals(2, converted[1])
    assertEquals("legacy", converted.id)
  }
}

package naksha.base

import naksha.base.Id.IdCompanion.ADMIN_CATALOG_INT
import naksha.base.Id.IdCompanion.ADMIN_CATALOG_NUMBER
import naksha.base.Id.IdCompanion.ADMIN_CATALOG_TEXT
import naksha.base.Id.IdCompanion.BOOKS_COL_INT
import naksha.base.Id.IdCompanion.BOOKS_COL_NUMBER
import naksha.base.Id.IdCompanion.BOOKS_COL_TEXT
import naksha.base.Id.IdCompanion.CATALOGS_COL_INT
import naksha.base.Id.IdCompanion.CATALOGS_COL_NUMBER
import naksha.base.Id.IdCompanion.CATALOGS_COL_TEXT
import naksha.base.Id.IdCompanion.COLLECTIONS_COL_INT
import naksha.base.Id.IdCompanion.COLLECTIONS_COL_NUMBER
import naksha.base.Id.IdCompanion.COLLECTIONS_COL_TEXT
import naksha.base.Id.IdCompanion.TRANSACTIONS_COL_INT
import naksha.base.Id.IdCompanion.TRANSACTIONS_COL_NUMBER
import naksha.base.Id.IdCompanion.TRANSACTIONS_COL_TEXT
import kotlin.test.Test
import kotlin.test.assertEquals

class IdTest {
    @Test
    fun predefinedCollectionNamesHaveFixedNumbers() {
        // naksha~admin
        assertEquals(ADMIN_CATALOG_NUMBER, Id(ADMIN_CATALOG_TEXT).number)
        assertEquals(ADMIN_CATALOG_INT, Id(ADMIN_CATALOG_TEXT).intValue)

        // naksha~collections
        assertEquals(COLLECTIONS_COL_NUMBER, Id(COLLECTIONS_COL_TEXT).number)
        assertEquals(COLLECTIONS_COL_INT, Id(COLLECTIONS_COL_TEXT).intValue)

        // naksha~catalogs
        assertEquals(CATALOGS_COL_NUMBER, Id(CATALOGS_COL_TEXT).number)
        assertEquals(CATALOGS_COL_INT, Id(CATALOGS_COL_TEXT).intValue)

        // naksha~transactions
        assertEquals(TRANSACTIONS_COL_NUMBER, Id(TRANSACTIONS_COL_TEXT).number)
        assertEquals(TRANSACTIONS_COL_INT, Id(TRANSACTIONS_COL_TEXT).intValue)

        // naksha~books
        assertEquals(BOOKS_COL_NUMBER, Id(BOOKS_COL_TEXT).number)
        assertEquals(BOOKS_COL_INT, Id(BOOKS_COL_TEXT).intValue)
    }

    @Test
    fun ensureReversingPredefinedIds() {
        assertEquals(ADMIN_CATALOG_TEXT, Id.numberToText(ADMIN_CATALOG_NUMBER))
        assertEquals(COLLECTIONS_COL_TEXT, Id.numberToText(COLLECTIONS_COL_NUMBER))
        assertEquals(CATALOGS_COL_TEXT, Id.numberToText(CATALOGS_COL_NUMBER))
        assertEquals(TRANSACTIONS_COL_TEXT, Id.numberToText(TRANSACTIONS_COL_NUMBER))
        assertEquals(BOOKS_COL_TEXT, Id.numberToText(BOOKS_COL_NUMBER))
    }
}

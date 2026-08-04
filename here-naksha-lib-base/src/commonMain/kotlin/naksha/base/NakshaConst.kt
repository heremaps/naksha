@file:OptIn(ExperimentalJsExport::class)

package naksha.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Some constants.
 * @since 3.0
 */
@JsExport
class NakshaConst private constructor() {
    companion object IdConst_C {
        /**
         * The maximum length of identifiers _(`42`)_.
         * @since 3.0
         */
        const val MAX_ID_LENGTH = 42 // The answer to everything ;-)

        /**
         * The maximum length of internal identifiers.
         * @since 3.0
         */
        const val MAX_INTERNAL_ID_LENGTH = 63

        /** Constant for the Naksha prefix that is reserved, no identifier must start with it: `naksha` */
        const val INTERNAL_PREFIX = "naksha~"

        /** Constant of the database `type` text */
        const val DATABASE_TYPE = "naksha.Database"

        /** Constant of the catalog `type` text */
        const val CATALOG_TYPE = "naksha.Catalog"

        /** Constant of the collection `type` text */
        const val COLLECTION_TYPE = "naksha.Collection"

        /** Constant of the feature `type` text */
        const val FEATURE_TYPE = "naksha.Feature"

        /** Constant of the transaction `type` text */
        const val TRANSACTION_TYPE = "naksha.Tx"

        /** Constant of the book `type` text */
        const val BOOK_TYPE = "naksha.Book"

        /** Constant of the member `type` text */
        const val MEMBER_TYPE = "naksha.Member"

        /** Constant of the index `type` text */
        const val INDEX_TYPE = "naksha.Index"

        // ── Well-known internal identifiers ──────────────────────────────────

        /** Text of the administration catalog identifier (`naksha~admin`). */
        const val ADMIN_CATALOG_TEXT = "${INTERNAL_PREFIX}admin"
        /** Quoted text of the administration catalog identifier (`"naksha~admin"`). */
        const val ADMIN_CATALOG_QUOTED = "\"${INTERNAL_PREFIX}admin\""
        /** Number of the administration catalog (fixed to `0`). */
        const val ADMIN_CATALOG_NUMBER = 0L

        /** Text of the collections-collection identifier (`naksha~collections`). */
        const val COLLECTIONS_COL_TEXT = "${INTERNAL_PREFIX}collections"
        /** Quoted text of the collections-collection identifier (`"naksha~collections"`). */
        const val COLLECTIONS_COL_QUOTED = "\"${INTERNAL_PREFIX}collections\""
        /** Number of the collections-collection (fixed to `0`). */
        const val COLLECTIONS_COL_NUMBER = 0L

        /** Text of the transactions-collection identifier (`naksha~transactions"`). */
        const val TRANSACTIONS_COL_TEXT = "${INTERNAL_PREFIX}transactions"
        /** Quoted text of the transactions-collection identifier (`"naksha~transactions"`). */
        const val TRANSACTIONS_COL_QUOTED = "\"${INTERNAL_PREFIX}transactions\""
        /** Number of the transactions-collection (fixed to `1`). */
        const val TRANSACTIONS_COL_NUMBER = 1L

        /** Text of the catalogs-collection identifier (`naksha~catalogs`). */
        const val CATALOGS_COL_TEXT = "${INTERNAL_PREFIX}catalogs"
        /** Quoted text of the catalogs-collection identifier (`"naksha~catalogs"`). */
        const val CATALOGS_COL_QUOTED = "\"${INTERNAL_PREFIX}catalogs\""
        /** Number of the catalogs-collection (fixed to `2`). */
        const val CATALOGS_COL_NUMBER = 2L

        /** Text of the books-collection identifier (`naksha~books`). */
        const val BOOKS_COL_TEXT = "${INTERNAL_PREFIX}books"
        /** Quoted text of the books-collection identifier (`"naksha~books"`). */
        const val BOOKS_COL_QUOTED = "\'${INTERNAL_PREFIX}books\'"
        /** Number of the books-collection (fixed to `3`). */
        const val BOOKS_COL_NUMBER = 3L
    }
}
@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.model.Naksha.NakshaCompanion.COLLECTIONS_COL
import naksha.model.Naksha.NakshaCompanion.COLLECTIONS_COL_NUMBER
import naksha.model.NakshaContext.NakshaContextCompanion.currentContext
import naksha.model.NakshaError.NakshaErrorCompanion.UNAUTHORIZED
import naksha.model.NakshaException
import naksha.model.objects.NakshaCollection
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * A map stores collections.
 */
@JsExport
open class PgMap internal constructor(
    /**
     * The reference to the storage.
     * @since 3.0.0
     */
    @JvmField val storage: PgStorage,

    /**
     * The map-id.
     */
    @JvmField val id: String,

    /**
     * The map-number.
     */
    @JvmField val number: Int,

    /**
     * The OID of the collection-number sequence.
     * @since 3.0.0
     */
    colNumberSequenceOid: Int
) {

    /**
     * The OID of the collection-number sequence.
     * @since 3.0.0
     */
    var colNumberSequenceOid: Int = colNumberSequenceOid
        protected set

    /**
     * The collections' collection.
     * @since 3.0.0
     */
    @Suppress("LeakingThis")
    @JvmField val collections: PgCollection = PgCollection(this, NakshaCollection()
        .withMapId(id)
        .withId(COLLECTIONS_COL)
        .withNumber(COLLECTIONS_COL_NUMBER)
    )

    /**
     * The map-identifier quoted optionally in double quotes.
     * @since 3.0.0
     */
    @JvmField
    val quotedId = quoteIdent(id)

    /**
     * Drop the schema.
     *
     * The method does auto-commit, if no [connection] was given; otherwise committing must be done explicitly.
     * @param connection the connection to use to query information from the database; if _null_, a new connection is used temporary.
     */
    open fun drop(connection: PgConnection? = null) {
        check(currentContext().su) { throw NakshaException(UNAUTHORIZED, "Only superusers may drop schemata") }
        val conn = connOf(connection)
        try {
            conn.execute("DROP SCHEMA ${quoteIdent(id)} CASCADE").close()
        } finally {
            closeOf(conn, connection, true)
        }
    }

    /**
     * Returns either the given connection, or opens a new admin connection, when the given connection is _null_.
     */
    private fun connOf(connection: PgConnection?): PgConnection = connection ?: storage.adminConnection()

    /**
     * The counter-part of [connOf], if the connection is _null_, closes [conn], if [commitOnClose] is _true_, commit changes before closing. Does nothing, when the [connection] is not _null_ ([commitOnClose] is ignored in this case).
     */
    private fun closeOf(conn: PgConnection, connection: PgConnection?, commitOnClose: Boolean) {
        if (conn !== connection) {
            if (commitOnClose) conn.commit()
            conn.close()
        }
    }
}
@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.Id.IdCompanion.ADMIN_CATALOG_TEXT

import naksha.base.*
import naksha.model.Tuple
import naksha.model.objects.NakshaCatalog
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import kotlin.js.JsExport
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

/**
 * A catalog containing collections.
 * @since 3.0
 */
@JsExport
open class PgCatalog @JvmOverloads constructor(
    /**
     * The _HEAD_ state of the catalog.
     * @since 3.0
     */
    @JvmField
    val tuple: Tuple,

    /**
     * The cached **read-only** [NakshaCatalog] representing of the [_HEAD_][tuple] state.
     *
     * ### DO NOT MODIFY THIS FEATURE
     * If you need a copy for modification, invoke [head.decodeCatalog()][Tuple.decodeCatalog].
     * @since 3.0
     */
    @JvmField // TODO: We need to make this immutable, we need better base classes!
    val naksha: NakshaCatalog = tuple.decodeCatalog(),
) {
    /**
     * The epoch time in milliseconds when created.
     * @since 3.0
     */
    @JvmField
    val cacheCreateMillis: Long = Platform.currentMillis()

    /**
     * The epoch time in milliseconds when last read.
     * @since 3.0
     */
    @JvmField
    var cacheReadMillis: Long = Platform.currentMillis()

    /**
     * Updates [cacheReadMillis] and returns this.
     * @since 3.0
     */
    fun readNow(): PgCatalog {
        cacheReadMillis = Platform.currentMillis()
        return this
    }

    /**
     * The catalog-identifier.
     * @since 3.0
     */
    @JvmField
    val id: Id = tuple.id

    /**
     * The name of the schema.
     * @since 3.0
     * @see quotedName
     */
    @JvmField
    val name: String = id.text

    /**
     * The quoted name of the schema, in double quotes _(if necessary)_.
     * @since 3.0
     * @see name
     */
    @JvmField
    val quotedName: String = quoteIdent(name)

    /**
     * The SQL command to be executed to set the `search_path`, so that this schema _(catalog)_ is on the top, followed by `naksha~admin`, `topology`, `hint_plan`, `public`.
     *
     * To be used like:
     * ```kotlin
     * conn.execute(catalog.SET_searchPath).close()
     * ```
     *
     * @since 3.0
     * @see applySearchPath
     */
    @JvmField
    val SET_searchPath: String = if (ADMIN_CATALOG_TEXT == id.text) {
        "SET search_path = \"naksha~admin\", topology, hint_plan, public"
    } else {
        "SET search_path = ${quotedName}, \"naksha~admin\", topology, hint_plan, public"
    }

    /**
     * Sets the `search_path` for the current transaction, so until `commit` or `rollback`.
     *
     * @param conn the connection where to set the search path.
     * @since 3.0
     * @see SET_searchPath
     */
    fun applySearchPath(conn: PgConnection) {
        conn.execute(SET_searchPath).close()
    }
}

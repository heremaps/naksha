package naksha.psql

import naksha.base.JsEnum
import naksha.base.fn.Fn2
import naksha.base.fn.Fx2
import naksha.base.fn.Fx3
import kotlin.js.JsExport
import kotlin.reflect.KClass

/**
 * The base class for indices.
 */
@Suppress("OPT_IN_USAGE", "MemberVisibilityCanBePrivate")
@JsExport
open class PgIndex : JsEnum() {
    // TODO: Finish this

    // var query = """CREATE ${unique}INDEX IF NOT EXISTS $qin ON $qtn USING btree
    // (id text_pattern_ops DESC)
    // WITH (fillfactor=$fillFactor) ${nakshaCollection.TABLESPACE};"""

    companion object {
        // TODO: Implement the three operations.

        /**
         * Index above the `id` column, the index will automatically be unique, when created in a HEAD table or HEAD partition.
         *
         * **Note**: This index can't be dropped!
         */
        val id = def(PgIndex::class, "id") { self ->
            self.existsFn = Fn2 { conn, table -> false }
            self.createFn = Fx3 { conn, table, isVolatile -> }
        }

        /**
         * Index above the `txn` and `uid` columns.
         */
        val txn_uid = def(PgIndex::class, "txn_uid") { self ->
            self.existsFn = Fn2 { conn, table -> false }
            self.createFn = Fx3 { conn, table, isVolatile -> }
            self.dropFn = Fx2 { conn, table -> }
        }

        /**
         * [GIST](https://www.postgresql.org/docs/current/gist.html) index above the `geo` and `txn` columns.
         */
        val geoGist_txn = def(PgIndex::class, "geoGist_txn") { self ->
            self.existsFn = Fn2 { conn, table -> false }
            self.createFn = Fx3 { conn, table, isVolatile -> }
            self.dropFn = Fx2 { conn, table -> }
        }

        /**
         * [SP-GIST](https://www.postgresql.org/docs/current/spgist.html) index above the `geo` and `txn` columns.
         */
        val geoSpGist_txn = def(PgIndex::class, "geoSpGist_txn") { self ->
            self.existsFn = Fn2 { conn, table -> false }
            self.createFn = Fx3 { conn, table, isVolatile -> }
            self.dropFn = Fx2 { conn, table -> }
        }

        /**
         * [BRIN](https://www.postgresql.org/docs/current/brin.html) index above the `geo` and `txn` columns.
         */
        val geoBrin_txn = def(PgIndex::class, "geoBrin_txn") { self ->
            self.existsFn = Fn2 { conn, table -> false }
            self.createFn = Fx3 { conn, table, isVolatile -> }
            self.dropFn = Fx2 { conn, table -> }
        }

        /**
         * Create a [GIN](https://www.postgresql.org/docs/current/gin.html) index above the `tags` and `txn` columns.
         */
        val tags_txn = def(PgIndex::class, "tags_txn") { self ->
            self.existsFn = Fn2 { conn, table -> false }
            self.createFn = Fx3 { conn, table, isVolatile -> }
            self.dropFn = Fx2 { conn, table -> }
        }

        /**
         * Index above the `grid` and `txn` columns.
         */
        val grid_txn = def(PgIndex::class, "grid_txn") { self ->
            self.existsFn = Fn2 { conn, table -> false }
            self.createFn = Fx3 { conn, table, isVolatile -> }
            self.dropFn = Fx2 { conn, table -> }
        }

        /**
         * Index above the `app_id`, `updated_at` and `txn` columns.
         */
        val appId_updatedAt_txn = def(PgIndex::class, "appId_updatedAt_txn") { self ->
            self.existsFn = Fn2 { conn, table -> false }
            self.createFn = Fx3 { conn, table, isVolatile -> }
            self.dropFn = Fx2 { conn, table -> }
        }

        /**
         * Index above the `author`, `author_ts` and `txn` columns.
         */
        val author_authorTs_txn = def(PgIndex::class, "author_authorTs_txn") { self ->
            self.existsFn = Fn2 { conn, table -> false }
            self.createFn = Fx3 { conn, table, isVolatile -> }
            self.dropFn = Fx2 { conn, table -> }
        }
    }

/*

val fillFactor = if (history) "100" else "70"
// https://www.postgresql.org/docs/current/gin-tips.html
val unique = if (history) "" else "UNIQUE "

// id
val qtn = PgUtil.quoteIdent(tableName) // quoted table name
var qin = PgUtil.quoteIdent("${tableName}_id_idx") // quoted index name
var query = """CREATE ${unique}INDEX IF NOT EXISTS $qin ON $qtn USING btree
(id text_pattern_ops DESC)
WITH (fillfactor=$fillFactor) ${nakshaCollection.TABLESPACE};"""

// txn, uid
qin = PgUtil.quoteIdent("${tableName}_txn_uid_idx")
query += """CREATE UNIQUE INDEX IF NOT EXISTS $qin ON $qtn USING btree
(txn DESC, COALESCE(uid, 0) DESC)
WITH (fillfactor=$fillFactor) ${nakshaCollection.TABLESPACE};"""

// geo, txn
qin = PgUtil.quoteIdent("${tableName}_geo_idx")
query += """CREATE INDEX IF NOT EXISTS $qin ON $qtn USING $geoIndex
(naksha_geometry(flags,geo), txn)
WITH (buffering=ON,fillfactor=$fillFactor) ${nakshaCollection.TABLESPACE} WHERE geo IS NOT NULL;"""

// tags, tnx
qin = PgUtil.quoteIdent("${tableName}_tags_idx")
query += """CREATE INDEX IF NOT EXISTS $qin ON $qtn USING gin
(tags_to_jsonb(tags), txn)
WITH (fastupdate=ON,gin_pending_list_limit=32768) ${nakshaCollection.TABLESPACE};"""

// grid, txn
qin = PgUtil.quoteIdent("${tableName}_grid_idx")
query += """CREATE INDEX IF NOT EXISTS $qin ON $qtn USING btree
(geo_grid DESC, txn DESC)
WITH (fillfactor=$fillFactor) ${nakshaCollection.TABLESPACE};"""

// app_id, updated_at, txn
qin = PgUtil.quoteIdent("${tableName}_app_id_idx")
query += """CREATE INDEX IF NOT EXISTS $qin ON $qtn USING btree
(app_id text_pattern_ops DESC, updated_at DESC, txn DESC)
WITH (fillfactor=$fillFactor) ${nakshaCollection.TABLESPACE};"""

// author, author_ts, txn
qin = PgUtil.quoteIdent("${tableName}_author_idx")
query += """CREATE INDEX IF NOT EXISTS $qin ON $qtn USING btree
(COALESCE(author, app_id) text_pattern_ops DESC, COALESCE(author_ts, updated_at) DESC, txn DESC)
WITH (fillfactor=$fillFactor) ${nakshaCollection.TABLESPACE};"""


 */

    protected var existsFn: Fn2<Boolean, PgConnection, PgTableInfo>? = null

    fun exists(conn: PgConnection, table: PgTableInfo): Boolean {
        val existsFn = this.existsFn
        check(existsFn != null) { "This index does not support `exists` operation" }
        return existsFn.call(conn, table)
    }

    protected var createFn: Fx3<PgConnection, PgTableInfo, Boolean>? = null
    fun create(conn: PgConnection, table: PgTableInfo, isVolatile: Boolean) {
        val createFn = this.createFn
        check(createFn != null) { "This index does not support `create` operation" }
        return createFn.call(conn, table, isVolatile)
    }

    protected var dropFn: Fx2<PgConnection, PgTableInfo>? = null
    fun drop(conn: PgConnection, table: PgTableInfo) {
        val dropFn = this.dropFn
        check(dropFn != null) { "This index does not support `drop` operation" }
        return dropFn.call(conn, table)
    }

    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = PgIndex::class

    override fun initClass() {
        register(PgIndex::class)
    }

}
package naksha.psql

import naksha.base.JsEnum
import naksha.base.fn.Fx2
import naksha.base.fn.Fx3
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import naksha.psql.PgColumn.PgColumnCompanion.id as c_id
import naksha.psql.PgColumn.PgColumnCompanion.txn as c_txn
import naksha.psql.PgColumn.PgColumnCompanion.uid as c_uid
import naksha.psql.PgColumn.PgColumnCompanion.flags as c_flags
import naksha.psql.PgColumn.PgColumnCompanion.app_id as c_app_id
import naksha.psql.PgColumn.PgColumnCompanion.author as c_author
import naksha.psql.PgColumn.PgColumnCompanion.author_ts as c_author_ts
import naksha.psql.PgColumn.PgColumnCompanion.updated_at as c_updated_at
import naksha.psql.PgColumn.PgColumnCompanion.geo as c_geo
import naksha.psql.PgColumn.PgColumnCompanion.geo_grid as c_geo_grid
import naksha.psql.PgColumn.PgColumnCompanion.tags as c_tags
import kotlin.js.JsExport
import kotlin.reflect.KClass

/**
 * The base class for indices. We have different kind of indices, but there is a general rule for all fuzzy indices.
 *
 * To query "fuzzy" indices (`GIST`, `SP-GIST` or `GIN` based), please select only the `id` from the fuzzy index, and then join the result with a more specific query. You can as well use multiple fuzzy indices in combination, for example to query a bounding box, where only certain tags are set:
 * ```
 * WITH geo AS (
 *   SELECT id
 *   FROM table
 *   WHERE st_intersects(naksha_geometry(flags,geo), ?)
 * )
 * WITH tags AS (
 *   SELECT id
 *   FROM geo g, table
 *   WHERE id=g.id
 *     AND naksha_tags(flags, tags) ? 'foo'
 *     AND naksha_tags(flags, tags)->'bar' = 13
 * )
 * SELECT c1, c2, ...
 * FROM tags t, table
 * WHERE t.id = id AND (...)
 * ORDER BY (...)
 * ```
 * This executes three query. The first one performs an index-only scan above the geometry index. The second one joins the geometry with the tags, using an index-only scan above tags. Eventually, joining the result with another query, that fetches everything else needed, optionally using another index that is already pre-sorted, because it is btree index. This allows to order without cost, even when query bounding boxes, history or tags.
 *
 * Beware that this query can be used in parallel in partitioned tables, because we partition HEAD, DELETE and HISTORY all by feature-id, we can run these queries in parallel using multiple connections (as many as we have partitions) and do not have to read feature duplicates!
 *
 * Another important note is that the unique immutable state of every feature is addressed by the columns `id`, `txn` and `uid`. Therefore, most indices exactly holds these values, which allows the client to only fetch them, and then to query caches to see if the corresponding state is already available to the client, improving the overall performance greatly. We even can create shared caches, so that information between systems only need these values to uniquely identify the feature details.
 */
@Suppress("OPT_IN_USAGE", "MemberVisibilityCanBePrivate")
@JsExport
open class PgIndex : JsEnum() {
    protected fun sql(using: String, table: PgTable, unique: Boolean, vola: Boolean): String = """
CREATE ${if (unique) "UNIQUE " else ""}INDEX IF NOT EXISTS ${quoteIdent(id(table))} ON ${table.quotedName}
USING $using
WITH (fillfactor=${if (vola) "65" else "100"}) ${table.TABLESPACE};"""

    companion object PgIndexCompanion {
        /**
         * A unique index above the [PgColumn.id] column.
         */
        val id = def(PgIndex::class, "id") { self ->
            self.columns = listOf(c_id)
            self.createFn = Fx3 { conn, table, vola ->
                conn.execute(
                    self.sql(
                        """btree ($c_id text_pattern_ops DESC)""",
                        table, true, vola
                    )
                ).close()
            }
        }

        /**
         * Index above the [PgColumn.id], [PgColumn.txn] and [PgColumn.uid] columns. To query this index above transaction numbers only, use `WHERE id like '%' AND txn ...`. If ordering is required, order via `ORDER BY id DESC, txn DESC, uid ASC`.
         */
        val id_txn_uid = def(PgIndex::class, "id_txn_uid") { self ->
            self.columns = listOf(c_id, c_txn, c_uid)
            self.createFn = Fx3 { conn, table, vola ->
                conn.execute(
                    self.sql(
                        """btree ($c_id text_pattern_ops DESC, $c_txn DESC, COALESCE($c_uid,0) ASC)""",
                        table, false, vola
                    )
                ).close()
            }
        }

        /**
         * [GIST](https://www.postgresql.org/docs/current/gist.html) index above [PgColumn.geo], [PgColumn.id], [PgColumn.txn] and [PgColumn.uid], for every collection that is **not** point-only.
         */
        val gist_geo_id_txn_uid = def(PgIndex::class, "gist_geo_id_txn_uid") { self ->
            self.columns = listOf(c_geo, c_id, c_txn, c_uid)
            self.createFn = Fx3 { conn, table, vola ->
                conn.execute(
                    self.sql(
                        """gist (naksha_geometry($c_flags,$c_geo), $c_id, $c_txn, $c_uid)""",
                        table, false, vola
                    )
                ).close()
            }
        }

        /**
         * [SP-GIST](https://www.postgresql.org/docs/current/spgist.html) index above [PgColumn.geo], [PgColumn.id], [PgColumn.txn] and [PgColumn.uid], better for point-only collections. For more details, please read in [gist_geo_id_txn_uid].
         */
        val spgist_geo_id_txn_uid = def(PgIndex::class, "spgist_geo_id_txn_uid") { self ->
            self.columns = listOf(PgColumn.geo, PgColumn.id, PgColumn.txn, PgColumn.uid)
            self.createFn = Fx3 { conn, table, vola ->
                conn.execute(
                    self.sql(
                        """sp-gist (naksha_geometry($c_flags,$c_geo), $c_id, $c_txn, $c_uid)""",
                        table, false, vola
                    )
                ).close()
            }
        }

        /**
         * Create a [GIN](https://www.postgresql.org/docs/current/gin.html) index above [PgColumn.tags], [PgColumn.id], [PgColumn.txn] and [PgColumn.uid].
         */
        val tags_id_txn_uid = def(PgIndex::class, "tags_id_txn_uid") { self ->
            self.columns = listOf(c_tags, c_id, c_txn, c_uid)
            self.createFn = Fx3 { conn, table, vola ->
                conn.execute(
                    self.sql(
                        """gin (naksha_tags($c_flags,$c_tags), $c_id, $c_txn, $c_uid)""",
                        table, false, vola
                    )
                ).close()
            }
        }

        /**
         * Index above [PgColumn.geo_grid], [PgColumn.id], [PgColumn.txn] and [PgColumn.uid].
         */
        val geo_grid_id_txn_uid = def(PgIndex::class, "geo_grid_id_txn_uid") { self ->
            self.columns = listOf(c_geo_grid, c_id, c_txn, c_uid)
            self.createFn = Fx3 { conn, table, vola ->
                conn.execute(
                    self.sql(
                        """btree ($c_geo_grid DESC, $c_id text_pattern_ops DESC, $c_txn DESC, COALESCE($c_uid,0) ASC)""",
                        table, false, vola
                    )
                ).close()
            }
        }

        /**
         * Index above [PgColumn.app_id], [PgColumn.updated_at], [PgColumn.id], [PgColumn.txn] and [PgColumn.uid].
         */
        val app_id_updatedAt_id_txn_uid = def(PgIndex::class, "app_id_updatedAt_id_txn_uid") { self ->
            self.columns = listOf(c_app_id, c_updated_at, c_id, c_txn, c_uid)
            self.createFn = Fx3 { conn, table, vola ->
                conn.execute(
                    self.sql(
                        """btree ($c_app_id text_pattern_ops DESC, $c_updated_at DESC, $c_id text_pattern_ops DESC, $c_txn DESC, COALESCE($c_uid,0) ASC)""",
                        table, false, vola
                    )
                ).close()
            }
        }

        /**
         * Index above the [PgColumn.author], [PgColumn.author_ts], [PgColumn.id], [PgColumn.txn] and [PgColumn.uid].
         */
        val author_ts_id_txn_uid = def(PgIndex::class, "author_ts_id_txn_uid") { self ->
            self.columns = listOf(c_author, c_author_ts, c_id, c_txn, c_uid)
            self.createFn = Fx3 { conn, table, vola ->
                conn.execute(
                    self.sql(
                        """btree ($c_author text_pattern_ops DESC, $c_author_ts DESC, $c_id text_pattern_ops DESC, $c_txn DESC, COALESCE($c_uid,0) ASC)""",
                        table, false, vola
                    )
                ).close()
            }
        }

        /**
         * Find the index by name, if such an index exists.
         * @param name as returned by the database from `pg_class` table as `relname` (with `relkind` being `i`).
         * @return the index, if it exists.
         */
        fun of(name: String): PgIndex? {
            val existing = getDefined(name, PgIndex::class)
            if (existing != null) return existing
            val start = name.lastIndexOf("\$i")
            if (start < 0) return null
            return getDefined(name.substring(start + "\$i".length), PgIndex::class)
        }
    }

    /**
     * Returns the unique identifier of this index in the given table.
     * @param table the table for which to generate the unique index name.
     * @return the unique identifier of this index in the given table.
     */
    fun id(table: PgTable): String = "${table.name}\$i_${str}"

    /**
     * The columns (in order) which are part of the index. This is more informational purpose, because the index can be much more complicated, for example it could be a partial index.
     */
    var columns: List<PgColumn> = emptyList()
        private set

    protected var createFn: Fx3<PgConnection, PgTable, Boolean>? = null
    internal fun create(conn: PgConnection, table: PgTable, vola: Boolean) {
        val createFn = this.createFn
        check(createFn != null) { "This index does not support `create` operation" }
        return createFn.call(conn, table, vola)
    }

    protected var dropFn: Fx2<PgConnection, PgTable>? = null
    internal fun drop(conn: PgConnection, table: PgTable) {
        dropFn?.call(conn, table) ?: conn.execute("DROP INDEX IF EXISTS ${quoteIdent(id(table))} CASCADE").close()
    }

    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = PgIndex::class

    override fun initClass() {
        register(PgIndex::class)
    }

}
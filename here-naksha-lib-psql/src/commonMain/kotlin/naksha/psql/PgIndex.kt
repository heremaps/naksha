package naksha.psql

import naksha.base.AtomicMap
import naksha.base.JsEnum
import naksha.base.fn.Fx2
import naksha.model.request.query.SortOrder
import naksha.model.request.query.SortOrder.SortOrderCompanion.DESCENDING
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import naksha.psql.PgColumn.PgColumnCompanion.id as c_id
import naksha.psql.PgColumn.PgColumnCompanion.fn as c_fn
import naksha.psql.PgColumn.PgColumnCompanion.version as c_version
import naksha.psql.PgColumn.PgColumnCompanion.next_version as c_next_version
import naksha.psql.PgColumn.PgColumnCompanion.flags as c_flags
import naksha.psql.PgColumn.PgColumnCompanion.app_id as c_app_id
import naksha.psql.PgColumn.PgColumnCompanion.author as c_author
import naksha.psql.PgColumn.PgColumnCompanion.author_ts as c_author_ts
import naksha.psql.PgColumn.PgColumnCompanion.updated_at as c_updated_at
import naksha.psql.PgColumn.PgColumnCompanion.geo as c_geo
import naksha.psql.PgColumn.PgColumnCompanion.here_tile as c_here_tile
import naksha.psql.PgColumn.PgColumnCompanion.tags as c_tags
import naksha.psql.PgColumn.PgColumnCompanion.ref_point as c_ref_point
import naksha.psql.PgColumn.PgColumnCompanion.ft as c_ft
import naksha.psql.PgColumn.PgColumnCompanion.cv0 as c_cv0
import naksha.psql.PgColumn.PgColumnCompanion.cv1 as c_cv1
import naksha.psql.PgColumn.PgColumnCompanion.cv2 as c_cv2
import naksha.psql.PgColumn.PgColumnCompanion.cv3 as c_cv3
import naksha.psql.PgColumn.PgColumnCompanion.cs0 as c_cs0
import naksha.psql.PgColumn.PgColumnCompanion.cs1 as c_cs1
import naksha.psql.PgColumn.PgColumnCompanion.cs2 as c_cs2
import naksha.psql.PgColumn.PgColumnCompanion.cs3 as c_cs3
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass

/**
 * The base class for indices. We have different kind of indices, but there is a general rule for all fuzzy indices.
 *
 * ## Warning
 * Identifiers in Postgres are limited to 63 byte (and otherwise will be truncated)!
 *
 * The index-name will look like `{collection-name:42}{postfix:15}$i_{index-name}`, this means
 * - The collection-names is up to 42 byte.
 * - The `postfix` is in the worst case `$hst$y????$p???`, so 15 byte.
 * - The index itself always starts with `$i_` which are 3 more byte.
 * - This means, we are left with only `63 - 42 - 15 - 3 = 3` byte for the index identifier.
 *
 * **This is why we use only three digit identifiers for the indices!**
 *
 */
@Suppress("OPT_IN_USAGE", "MemberVisibilityCanBePrivate")
@JsExport
open class PgIndex : JsEnum() {
    // TODO: We need to allow `CREATE INDEX CONCURRENTLY`, when the index is not unique.
    //       However, this can only be done, when being outside of a transaction!
    //       We need to add special support for index modification outside of transactions.
    //       So, when a client modifies a collection, we can use a second connection in auto-commit, and
    //       create missing indices with a special query.
    //       Storage-API then needs to create a table brittle, without indices, except for minimal ones,
    //         then after importing, it should switch tables to logged, then add indices, which should be
    //         done concurrently or the client needs to wait until the index is build, but then we need
    //         to add an very large timeout.
    protected fun sql(using: String, table: PgTable, unique: Boolean, addFillFactor: Boolean, where: String?): String {
        // HEAD and META tables have no `next_version` column, so any reference must be stripped.
        val finalUsing = if (PgTable.isAnyHead(table.name) || PgTable.isMeta(table.name)) {
            using
                .replace("INCLUDE (${c_next_version.name})", "") // standalone INCLUDE clause
                .replace(", ${c_next_version.name}", "")          // suffix in column list
                .replace("${c_next_version.name}, ", "")          // prefix in column list
        } else using
        return """
CREATE ${if (unique) "UNIQUE INDEX" else "INDEX "} IF NOT EXISTS ${quoteIdent(id(table))} ON ${table.quotedName}
USING $finalUsing
${if (addFillFactor) "WITH (fillfactor="+if (table.isVolatile) "80)" else "100)" else ""} ${table.TABLESPACE}
${if (where==null) "" else "WHERE $where"};"""
    }

    companion object PgIndex_C {
        /**
         * The primary key about the [fn][PgColumn.fn] (and, for HISTORY-side tables, [version][PgColumn.version]).
         *
         * This entry is not used directly to create the index, its only formally here because the column itself has the attribute `PRIMARY KEY`, which is important for joins, replication, and such things. This constraint will always be named automatically by postgres as `table_pkey`. The actual PK DDL is emitted from [PgTable]; the [createFn] below is informational only.
         *
         * - Always added to all tables as PRIMARY KEY!
         * @see [PgAdminMap.createPgCollection]
         */
        @JvmField
        @JsStatic
        val tn_pkey = def(PgIndex::class, "tnu") { self ->
            self.name = "tn_unique"
            self.internal = true
            self.columns = listOf(c_fn)
            self.naturalOrder = listOf(DESCENDING)
            self.includes = emptyList()
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        """btree ($c_fn DESC)""",
                        table, unique = true, addFillFactor = true, where = null
                    )
                ).close()
            }
        }

        /**
         * A unique index above the [id][PgColumn.id], including [fn][PgColumn.fn], [version][PgColumn.version] and [next_version][PgColumn.next_version] column.
         *
         * - Automatically added to [HEAD][PgHead], [DELETED][PgDeleted], and [META][PgMeta].
         * - Must not be added to [HISTORY][PgHistory].
         * @see [PgAdminMap.createPgCollection]
         */
        @JvmField
        @JsStatic
        val id_unique = def(PgIndex::class, "idu") { self ->
            self.name = "id_unique"
            self.internal = true
            self.columns = listOf(c_id)
            self.naturalOrder = listOf(DESCENDING)
            self.includes = listOf(c_fn, c_version, c_next_version)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        """btree ($c_id text_pattern_ops DESC) INCLUDE ($c_fn, $c_version, $c_next_version)""",
                        table, unique = true, addFillFactor = true, where = null
                    )
                ).close()
            }
        }

        /**
         * A non-unique index above the [id][PgColumn.id], [fn][PgColumn.fn] and [version][PgColumn.version], including [next_version][PgColumn.next_version] column.
         *
         * - Automatically added to [HISTORY][PgHistory].
         * - Must not be added to [HEAD][PgHead], [DELETED][PgDeleted], and [META][PgMeta].
         * @see [PgAdminMap.createPgCollection]
         */
        @JvmField
        @JsStatic
        val id = def(PgIndex::class, "idi") { self ->
            self.name = "id"
            self.internal = true
            self.columns = listOf(c_id, c_fn, c_version)
            self.naturalOrder = listOf(DESCENDING, DESCENDING, DESCENDING)
            self.includes = listOf(c_next_version)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        """btree ($c_id text_pattern_ops DESC, $c_fn DESC, $c_version DESC) INCLUDE ($c_next_version)""",
                        table, unique = false, addFillFactor = true, where = null
                    )
                ).close()
            }
        }

        /**
         * A non-unique index above the [version][PgColumn.version].
         *
         * - Always added to all tables.
         * @see [PgAdminMap.createPgCollection]
         */
        @JvmField
        @JsStatic
        val version = def(PgIndex::class, "ver") { self ->
            self.name = "version"
            self.internal = true
            self.columns = listOf(c_version)
            self.naturalOrder = listOf(DESCENDING)
            self.includes = listOf(c_fn, c_id, c_next_version)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        """btree ($c_version DESC) INCLUDE ($c_fn, $c_id, $c_next_version)""",
                        table, unique = false, addFillFactor = true, where = null
                    )
                ).close()
            }
        }

        /**
         * A unique index above the transaction-number _(aka [version][PgColumn.version])_, including [id][PgColumn.id], [fn][PgColumn.fn] and [next_version][PgColumn.next_version] column.
         *
         * - Automatically added to all [TRANSACTIONS][PgTransactions] tables.
         * - Must not be added to any other table.
         * @see [PgAdminMap.createPgCollection]
         */
        @JvmField
        @JsStatic
        val txn_unique = def(PgIndex::class, "txn") { self ->
            self.name = "txn_unique"
            self.internal = true
            self.columns = listOf()
            self.naturalOrder = listOf(DESCENDING)
            self.includes = listOf(c_id, c_fn, c_version, c_next_version)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        """btree (($c_version & -4) DESC) INCLUDE ($c_fn, $c_version, $c_id, $c_next_version)""",
                        table, unique = true, addFillFactor = true, where = null
                    )
                ).close()
            }
        }

        /**
         * Index above [here_tile][PgColumn.here_tile], [fn][PgColumn.fn] and [version][PgColumn.version], including [id][PgColumn.id] and [next_version][PgColumn.next_version].
         *
         * Ordered by:
         * - `here_tile` DESC
         * - `fn` DESC
         * - `version` DESC
         * @see [PgAdminMap.createPgCollection]
         */
        @JvmField
        @JsStatic
        val here_tile = def(PgIndex::class, "hti") { self ->
            self.name = "here_tile"
            self.columns = listOf(c_here_tile, c_fn, c_version)
            self.naturalOrder = listOf(DESCENDING, DESCENDING, DESCENDING)
            self.includes = listOf(c_id, c_next_version)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        "btree ($c_here_tile DESC, $c_fn DESC, $c_version DESC) INCLUDE ($c_id, $c_next_version)",
                        table, unique = false, addFillFactor = true, where = "$c_here_tile IS NOT NULL"
                    )
                ).close()
            }
        }

        /**
         * Index above [app_id][PgColumn.app_id], [updated_at][PgColumn.updated_at], [fn][PgColumn.fn] and [version][PgColumn.version], including [id][PgColumn.id] and [next_version][PgColumn.next_version].
         *
         * Ordered by
         * - `app_id` DESC
         * - `updated_at` DESC
         * - `fn` DESC
         * - `version` DESC
         * @see [PgAdminMap.createPgCollection]
         */
        @JvmField
        @JsStatic
        val app_id = def(PgIndex::class, "aid") { self ->
            self.name = "app_id"
            self.columns = listOf(c_app_id, c_updated_at, c_fn, c_version)
            self.naturalOrder = listOf(DESCENDING, DESCENDING, DESCENDING, DESCENDING)
            self.includes = listOf(c_id, c_next_version)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        """btree ($c_app_id text_pattern_ops DESC, $c_updated_at DESC, $c_fn DESC, $c_version DESC) INCLUDE ($c_id, $c_next_version)""",
                        table, unique = false, addFillFactor = true, where = "$c_app_id IS NOT NULL"
                    )
                ).close()
            }
        }

        /**
         * Index above the `naksha_author(`[author][PgColumn.author], [app_id][PgColumn.app_id]`)`, `naksha_author_ts(`[author_ts][PgColumn.author_ts], [updated_at][PgColumn.updated_at]`)`, [fn][PgColumn.fn] and [version][PgColumn.version], including [id][PgColumn.id] and [next_version][PgColumn.next_version].
         *
         * Ordered by:
         * - `naksha_author(author, app_id)` DESC
         * - `naksha_author_ts(author_ts, updated_at)` DESC
         * - `fn` DESC
         * - `version` DESC
         * @see [PgAdminMap.createPgCollection]
         */
        @JvmField
        @JsStatic
        val author = def(PgIndex::class, "ath") { self ->
            self.name = "author"
            self.columns = listOf(c_author, c_author_ts, c_fn, c_version)
            self.naturalOrder = listOf(DESCENDING, DESCENDING, DESCENDING, DESCENDING)
            self.includes = listOf(c_id, c_next_version)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        """btree (naksha_author($c_author, $c_app_id) text_pattern_ops DESC, naksha_author_ts($c_author_ts, $c_updated_at) DESC, $c_fn DESC, $c_version DESC) INCLUDE ($c_id, $c_next_version)""",
                        table, unique = false, addFillFactor = true, where = "naksha_author($c_author, $c_app_id) IS NOT NULL"
                    )
                ).close()
            }
        }

        /**
         * A [GIN](https://www.postgresql.org/docs/current/gin.html) index above `naksha_tags(`[tags][PgColumn.tags], [flags][PgColumn.flags]`)`, [fn][PgColumn.fn], [version][PgColumn.version], and [next_version][PgColumn.next_version].
         * @see [PgAdminMap.createPgCollection]
         */
        @JvmField
        @JsStatic
        val tags = def(PgIndex::class, "tag") { self ->
            self.name = "tags"
            self.columns = listOf(c_tags, c_fn, c_version, c_next_version)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        """gin (naksha_tags($c_tags, $c_flags), $c_fn, $c_version, $c_next_version)""",
                        table, unique = false, addFillFactor = false, where = "naksha_tags($c_tags, $c_flags) IS NOT NULL"
                    )
                ).close()
            }
        }

        /**
         * A two-dimensional [SP-GIST](https://www.postgresql.org/docs/current/spgist.html) index above `naksha_ref_point(`[PgColumn.ref_point]`)`.
         * @see [PgAdminMap.createPgCollection]
         */
        @JvmField
        @JsStatic
        val ref_point = def(PgIndex::class, "ref") { self ->
            self.name = "ref_point"
            self.columns = listOf(c_ref_point)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        """spgist (naksha_ref_point($c_ref_point))""",
                        table, unique = false, addFillFactor = true, where = "naksha_ref_point($c_ref_point) IS NOT NULL"
                    )
                ).close()
            }
        }

        /**
         * A two-dimensional [GIST](https://www.postgresql.org/docs/current/gist.html) index above `naksha_2d(`[PgColumn.geo], [PgColumn.flags]`)`.
         * @see [PgAdminMap.createPgCollection]
         */
        @JvmField
        @JsStatic
        val gist_geo = def(PgIndex::class, "g2d") { self ->
            self.name = "gist_geo"
            self.columns = listOf(c_geo, c_fn, c_version, c_next_version)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        """gist (naksha_2d($c_geo, $c_flags), $c_fn, $c_version, $c_next_version)""",
                        table, unique = false, addFillFactor = true, where = "naksha_2d($c_geo, $c_flags) IS NOT NULL"
                    )
                ).close()
            }
        }

        /**
         * A two-dimensional [GIST](https://www.postgresql.org/docs/current/gist.html) index above `naksha_2d(`[PgColumn.geo], [PgColumn.flags]`)`.
         * @see [PgAdminMap.createPgCollection]
         */
        @JvmField
        @JsStatic
        val spgist_geo = def(PgIndex::class, "s2d") { self ->
            self.name = "spgist_geo"
            self.columns = listOf(c_geo)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        """spgist (naksha_2d($c_geo, $c_flags))""",
                        table, unique = false, addFillFactor = true, where = "naksha_2d($c_geo, $c_flags) IS NOT NULL"
                    )
                ).close()
            }
        }

        /**
         * Index above [feature-type][PgColumn.ft], [fn][PgColumn.fn] and [version][PgColumn.version], including [id][PgColumn.id] and [next_version][PgColumn.next_version].
         * @see [PgAdminMap.createPgCollection]
         */
        @JvmField
        @JsStatic
        val ft = def(PgIndex::class, "ft") { self ->
            self.name = "feature_type"
            self.columns = listOf(c_ft, c_fn, c_version)
            self.naturalOrder = listOf(DESCENDING, DESCENDING, DESCENDING)
            self.includes = listOf(c_id, c_next_version)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        "btree ($c_ft text_pattern_ops DESC, $c_fn DESC, $c_version DESC) INCLUDE ($c_id, $c_next_version)",
                        table, unique = false, addFillFactor = true, where = "$c_ft IS NOT NULL"
                    )
                ).close()
            }
        }.alias<PgIndex>("feature_type").alias<PgIndex>("featureType")

        /**
         * Index above [cv0][PgColumn.cv0], [fn][PgColumn.fn] and [version][PgColumn.version], including [id][PgColumn.id] and [next_version][PgColumn.next_version].
         * @see [PgAdminMap.createPgCollection]
         */
        @JvmField
        @JsStatic
        val cv0 = def(PgIndex::class, "cv0") { self ->
            self.name = "cv0"
            self.columns = listOf(c_cv0, c_fn, c_version)
            self.naturalOrder = listOf(DESCENDING, DESCENDING, DESCENDING)
            self.includes = listOf(c_id, c_next_version)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        "btree ($c_cv0 DESC, $c_fn DESC, $c_version DESC) INCLUDE ($c_id, $c_next_version)",
                        table, unique = false, addFillFactor = true, where = "$c_cv0 IS NOT NULL"
                    )
                ).close()
            }
        }

        /**
         * Index above [cv1][PgColumn.cv1], [fn][PgColumn.fn] and [version][PgColumn.version], including [id][PgColumn.id] and [next_version][PgColumn.next_version].
         * @see [PgAdminMap.createPgCollection]
         */
        @JvmField
        @JsStatic
        val cv1 = def(PgIndex::class, "cv1") { self ->
            self.name = "cv1"
            self.columns = listOf(c_cv1, c_fn, c_version)
            self.naturalOrder = listOf(DESCENDING, DESCENDING, DESCENDING)
            self.includes = listOf(c_id, c_next_version)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        "btree ($c_cv1 DESC, $c_fn DESC, $c_version DESC) INCLUDE ($c_id, $c_next_version)",
                        table, unique = false, addFillFactor = true, where = "$c_cv1 IS NOT NULL"
                    )
                ).close()
            }
        }

        /**
         * Index above [cv2][PgColumn.cv2], [fn][PgColumn.fn] and [version][PgColumn.version], including [id][PgColumn.id] and [next_version][PgColumn.next_version].
         * @see [PgAdminMap.createPgCollection]
         */
        @JvmField
        @JsStatic
        val cv2 = def(PgIndex::class, "cv2") { self ->
            self.name = "cv2"
            self.columns = listOf(c_cv2, c_fn, c_version)
            self.naturalOrder = listOf(DESCENDING, DESCENDING, DESCENDING)
            self.includes = listOf(c_id, c_next_version)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        "btree ($c_cv2 DESC, $c_fn DESC, $c_version DESC) INCLUDE ($c_id, $c_next_version)",
                        table, unique = false, addFillFactor = true, where = "$c_cv2 IS NOT NULL"
                    )
                ).close()
            }
        }

        /**
         * Index above [cv3][PgColumn.cv3], [fn][PgColumn.fn] and [version][PgColumn.version], including [id][PgColumn.id] and [next_version][PgColumn.next_version].
         * @see [PgAdminMap.createPgCollection]
         */
        @JvmField
        @JsStatic
        val cv3 = def(PgIndex::class, "cv3") { self ->
            self.name = "cv3"
            self.columns = listOf(c_cv3, c_fn, c_version)
            self.naturalOrder = listOf(DESCENDING, DESCENDING, DESCENDING)
            self.includes = listOf(c_id, c_next_version)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        "btree ($c_cv3 DESC, $c_fn DESC, $c_version DESC) INCLUDE ($c_id, $c_next_version)",
                        table, unique = false, addFillFactor = true, where = "$c_cv3 IS NOT NULL"
                    )
                ).close()
            }
        }

        /**
         * Index above [cs0][PgColumn.cs0], [fn][PgColumn.fn] and [version][PgColumn.version], including [id][PgColumn.id] and [next_version][PgColumn.next_version].
         * @see [PgAdminMap.createPgCollection]
         */
        @JvmField
        @JsStatic
        val cs0 = def(PgIndex::class, "cs0") { self ->
            self.name = "cs0"
            self.columns = listOf(c_cs0, c_fn, c_version)
            self.naturalOrder = listOf(DESCENDING, DESCENDING, DESCENDING)
            self.includes = listOf(c_id, c_next_version)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        "btree ($c_cs0 DESC, $c_fn DESC, $c_version DESC) INCLUDE ($c_id, $c_next_version)",
                        table, unique = false, addFillFactor = true, where = "$c_cs0 IS NOT NULL"
                    )
                ).close()
            }
        }

        /**
         * Index above [cs1][PgColumn.cs1], [fn][PgColumn.fn] and [version][PgColumn.version], including [id][PgColumn.id] and [next_version][PgColumn.next_version].
         * @see [PgAdminMap.createPgCollection]
         */
        @JvmField
        @JsStatic
        val cs1 = def(PgIndex::class, "cs1") { self ->
            self.name = "cs1"
            self.columns = listOf(c_cs1, c_fn, c_version)
            self.naturalOrder = listOf(DESCENDING, DESCENDING, DESCENDING)
            self.includes = listOf(c_id, c_next_version)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        "btree ($c_cs1 DESC, $c_fn DESC, $c_version DESC) INCLUDE ($c_id, $c_next_version)",
                        table, unique = false, addFillFactor = true, where = "$c_cs1 IS NOT NULL"
                    )
                ).close()
            }
        }

        /**
         * Index above [cs2][PgColumn.cs2], [fn][PgColumn.fn] and [version][PgColumn.version], including [id][PgColumn.id] and [next_version][PgColumn.next_version].
         * @see [PgAdminMap.createPgCollection]
         */
        @JvmField
        @JsStatic
        val cs2 = def(PgIndex::class, "cs2") { self ->
            self.name = "cs2"
            self.columns = listOf(c_cs2, c_fn, c_version)
            self.naturalOrder = listOf(DESCENDING, DESCENDING, DESCENDING)
            self.includes = listOf(c_id, c_next_version)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        "btree ($c_cs2 DESC, $c_fn DESC, $c_version DESC) INCLUDE ($c_id, $c_next_version)",
                        table, unique = false, addFillFactor = true, where = "$c_cs2 IS NOT NULL"
                    )
                ).close()
            }
        }

        /**
         * Index above [cs3][PgColumn.cs3], [fn][PgColumn.fn] and [version][PgColumn.version], including [id][PgColumn.id] and [next_version][PgColumn.next_version].
         * @see [PgAdminMap.createPgCollection]
         */
        @JvmField
        @JsStatic
        val cs3 = def(PgIndex::class, "cs3") { self ->
            self.name = "cs3"
            self.columns = listOf(c_cs3, c_fn, c_version)
            self.naturalOrder = listOf(DESCENDING, DESCENDING, DESCENDING)
            self.includes = listOf(c_id, c_next_version)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        "btree ($c_cs3 DESC, $c_fn DESC, $c_version DESC) INCLUDE ($c_id, $c_next_version)",
                        table, unique = false, addFillFactor = true, where = "$c_cs3 IS NOT NULL"
                    )
                ).close()
            }
        }

        /**
         * Truncates the identifier to the minimal size that is guaranteed.
         * @param id the index identifier.
         * @return the identifier truncated to the minimal guaranteed length.
         */
        @JvmStatic
        @JsStatic
        fun truncate(id: String): String = if (id.length > 3) id.substring(0, 3) else id

        /**
         * Find the index by name or _relname_.
         * @param name the official [name] or the relation name (`relname`), as returned by the database from the `pg_class` table (with `relkind` being `i`).
         * @return the index, if it exists.
         */
        @JvmStatic
        @JsStatic
        fun of(name: String): PgIndex? {
            val existing = getDefined(name, PgIndex::class) ?: indexByName[name]
            if (existing != null) return existing
            val start = name.lastIndexOf(PG_IDX)
            if (start < 0) return null
            // This is a hack for PostgresQL, which will truncate identifiers to 63 byte.
            // Therefore, we know that name is limited to 63 characters, which may have truncated the index identifier.
            // So we extract what is left from the index identifier and the compare it against all enumeration values.
            // Note: It could only have truncated the last byte or many more, dependent on how long the collection id is!
            val pg_truncated_id = name.substring(start + PG_IDX.length)
            for (e in iterate(PgIndex::class)) if (e.text.startsWith(pg_truncated_id)) return e
            return null
        }

        /**
         * Indices by official name.
         * @since 3.0.0
         */
        private val indexByName = AtomicMap<String, PgIndex>()
        init {
            // Sanity check.
            val byName = indexByName
            val map = HashMap<String, PgIndex>()
            for (index in iterate(PgIndex::class)) {
                val pg_truncated_id = truncate(index.text)
                if (pg_truncated_id in map) {
                    val c = map[pg_truncated_id]!!
                    throw Error("Conflict, the index ${index.text} has the same short name as ${c.text}: $pg_truncated_id")
                }
                map[pg_truncated_id] = index

                val e = byName.putIfAbsent(index.name, index)
                if (e != null) throw Error("Conflict, the index ${index.text} has the same official name as ${e.text}: ${e.name}")
            }
        }

        /**
         * The list of default index names to be are added, when _null_ is provided as index list to create.
         */
        @JvmField
        @JsStatic
        var DEFAULT_INDICES = listOf(
            id,
            here_tile,
            app_id,
            author,
            tags,
            ft,
            cv0, cv1, cv2, cv3,
            cs0, cs1, cs2, cs3,
            ref_point,
            gist_geo,
        )
    }

    /**
     * If the index is internal, that means it is not intentionally manageable from clients.
     * @since 3.0
     */
    var internal: Boolean = false
        private set

    private var _name: String? = null

    /**
     * The official index name to be used in [naksha.model.objects.NakshaCollection.indices].
     * @since 3.0.0
     */
    var name: String
        get() = _name ?: text
        protected set(value) {
            _name = value
        }

    /**
     * Returns the unique identifier of this index in the given table.
     * @param table the table for which to generate the unique index name.
     * @return the unique identifier of this index in the given table.
     */
    fun id(table: PgTable): String {
        val id = "${table.name}${PG_IDX}${text}"
        return if (id.length > 63) id.substring(0, 63) else id
    }

    /**
     * Returns the unique identifier of this index in the table with the given name.
     * @param tableName the name of the table for which to generate the unique index name.
     * @return the unique identifier of this index in the given table.
     */
    @JsName("idByTableName")
    fun id(tableName: String): String {
        val id = "${tableName}${PG_IDX}${text}"
        return if (id.length > 63) id.substring(0, 63) else id
    }

    /**
     * The columns (in order) which are part of the index.
     *
     * This is only informational purpose, because the index can be much more complicated, for example it could be a partial index, and it does not contain columns only included in the index, see [includes].
     */
    var columns: List<PgColumn> = emptyList()
        protected set

    /**
     * The natural sort order of the index, should hold one entry for each one in [columns].
     *
     * **Note**: If the sort-order is empty, but [columns] is not, this means that the index does not support sorting, e.g. `GIN` or `GIST` indices.
     */
    var naturalOrder: List<SortOrder> = emptyList()
        protected set

    /**
     * The columns being included only in the index.
     */
    var includes: List<PgColumn> = emptyList()
        protected set

    protected var createFn: Fx2<PgConnection, PgTable>? = null
    internal fun create(conn: PgConnection, table: PgTable) {
        val createFn = this.createFn
        check(createFn != null) { "This index does not support `create` operation" }
        return createFn.call(conn, table)
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
package naksha.psql

import naksha.base.AtomicMap
import naksha.base.JsEnum
import naksha.base.fn.Fx2
import naksha.model.request.query.SortOrder
import naksha.model.request.query.SortOrder.SortOrderCompanion.DESCENDING
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import naksha.psql.PgColumn.PgColumnCompanion.id as c_id
import naksha.psql.PgColumn.PgColumnCompanion.tn as c_tn
import naksha.psql.PgColumn.PgColumnCompanion.txn as c_txn
import naksha.psql.PgColumn.PgColumnCompanion.uid as c_uid
import naksha.psql.PgColumn.PgColumnCompanion.txn_next as c_txn_next
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
    protected fun sql(using: String, table: PgTable, unique: Boolean, addFillFactor: Boolean): String = """
CREATE ${if (unique) "UNIQUE " else ""}INDEX IF NOT EXISTS ${quoteIdent(id(table))} ON ${table.quotedName}
USING $using
${if (addFillFactor) "WITH (fillfactor="+if (table.isVolatile) "65)" else "100)" else ""} ${table.TABLESPACE};"""

    companion object PgIndex_C {
        /**
         * The primary key about the tuple-number.
         *
         * - Always added to all tables!
         */
        @JvmField
        @JsStatic
        val tn_pkey = def(PgIndex::class, "tnp") { self ->
            self.name = "tn_pkey"
            self.columns = listOf(c_tn)
            self.naturalOrder = listOf(DESCENDING)
            self.includes = emptyList()
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        """btree ($c_tn DESC)""",
                        table, unique = true, addFillFactor = true
                    )
                ).close()
            }
        }

        /**
         * A unique index above the [PgColumn.id] column.
         *
         * - Automatically added to [HEAD][PgHead], [DELETED][PgDeleted], and [META][PgMeta] tables in [PgCollection.create].
         */
        @JvmField
        @JsStatic
        val id_unique = def(PgIndex::class, "idu") { self ->
            self.name = "id_unique"
            self.columns = listOf(c_id)
            self.naturalOrder = listOf(DESCENDING)
            self.includes = listOf(c_tn)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        """btree ($c_id text_pattern_ops DESC) INCLUDE ($c_tn)""",
                        table, unique = true, addFillFactor = true
                    )
                ).close()
            }
        }

        /**
         * A unique index above the [PgColumn.txn] column.
         *
         * - Automatically added to all [TRANSACTIONS][PgTransactions] tables in [PgCollection.create].
         */
        @JvmField
        @JsStatic
        val txn_unique = def(PgIndex::class, "txn") { self ->
            self.name = "txn_unique"
            self.columns = listOf(c_txn)
            self.naturalOrder = listOf(DESCENDING)
            self.includes = listOf(c_tn)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        """btree ($c_txn DESC) INCLUDE ($c_id)""",
                        table, unique = true, addFillFactor = true
                    )
                ).close()
            }
        }

        /**
         * A non-unique index above the [PgColumn.id], [PgColumn.txn], [PgColumn.uid], and [PgColumn.txn_next] column.
         *
         * We do not need uniqueness, because we already have a unique index for [tn][tn_pkey], which includes `txn`, and `uid`, combined with the unique index above [id][id_unique] in HEAD, DELETED, and META, it is passively guaranteed that the combination (`id`, `txn`, `uid`) is unique in all tables, including HISTORY.
         */
        @JvmField
        @JsStatic
        val id = def(PgIndex::class, "itu") { self ->
            self.name = "id"
            self.columns = listOf(c_id, c_txn, c_uid, c_txn_next)
            self.naturalOrder = listOf(DESCENDING, DESCENDING, DESCENDING, DESCENDING)
            self.includes = listOf(c_tn)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        """btree ($c_id text_pattern_ops DESC, $c_txn DESC, $c_uid DESC, $c_txn_next DESC) INCLUDE ($c_tn)""",
                        table, unique = false, addFillFactor = true
                    )
                ).close()
            }
        }

        /**
         * Index above [PgColumn.here_tile], [PgColumn.id], [PgColumn.txn], [PgColumn.uid] and [PgColumn.txn_next].
         */
        @JvmField
        @JsStatic
        val here_tile = def(PgIndex::class, "hti") { self ->
            self.name = "here_tile"
            self.columns = listOf(c_here_tile, c_id, c_txn, c_uid, c_txn_next)
            self.naturalOrder = listOf(DESCENDING, DESCENDING, DESCENDING)
            self.includes = listOf(c_tn)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        "btree ($c_here_tile DESC, $c_id text_pattern_ops DESC, $c_txn DESC, $c_uid DESC, $c_txn_next DESC) INCLUDE ($c_tn)",
                        table, unique = false, addFillFactor = true
                    )
                ).close()
            }
        }

        /**
         * Index above [PgColumn.app_id], [PgColumn.updated_at], [PgColumn.id], [PgColumn.txn], [PgColumn.uid], and [PgColumn.txn_next].
         *
         * Ordered descending:
         * ```
         * ORDER BY app_id desc, updated_at desc, id desc, txn desc, uid desc, txn_next desc
         * ```
         */
        @JvmField
        @JsStatic
        val app_id = def(PgIndex::class, "aid") { self ->
            self.name = "app_id"
            self.columns = listOf(c_app_id, c_updated_at, c_id, c_txn, c_uid, c_txn_next)
            self.naturalOrder = listOf(DESCENDING, DESCENDING, DESCENDING, DESCENDING, DESCENDING, DESCENDING)
            self.includes = listOf(c_tn)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        """btree ($c_app_id text_pattern_ops DESC, $c_updated_at DESC, $c_id text_pattern_ops DESC, $c_txn DESC, $c_uid DESC, $c_txn_next DESC) INCLUDE ($c_tn)""",
                        table, unique = false, addFillFactor = true
                    )
                ).close()
            }
        }

        /**
         * Index above the `naksha_author(`[PgColumn.author], [PgColumn.app_id]`)`, `naksha_author_ts(`[PgColumn.author_ts], [PgColumn.updated_at]`)`, [PgColumn.id], [PgColumn.txn], [PgColumn.uid], and [PgColumn.txn_next].
         *
         * Ordered descending:
         * ```
         * ORDER BY naksha_author(author, app_id) desc, naksha_author_ts(author_ts, updated_at) desc, id desc, txn desc, uid desc, txn_next desc
         * ```
         */
        @JvmField
        @JsStatic
        val author = def(PgIndex::class, "ath") { self ->
            self.name = "author"
            self.columns = listOf(c_author, c_author_ts, c_id, c_txn, c_uid, c_txn_next)
            self.naturalOrder = listOf(DESCENDING, DESCENDING, DESCENDING, DESCENDING, DESCENDING, DESCENDING)
            self.includes = listOf(c_tn)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        """btree (naksha_author($c_author, $c_app_id) text_pattern_ops DESC, naksha_author_ts($c_author_ts, $c_updated_at) DESC, $c_id text_pattern_ops DESC, $c_txn DESC, $c_uid DESC, $c_txn_next DESC) INCLUDE ($c_tn)""",
                        table, unique = false, addFillFactor = true
                    )
                ).close()
            }
        }

        /**
         * A [GIN](https://www.postgresql.org/docs/current/gin.html) index above `naksha_tags(`[PgColumn.tags], [PgColumn.flags]`)`.
         */
        @JvmField
        @JsStatic
        val tags = def(PgIndex::class, "tag") { self ->
            self.name = "tags"
            self.columns = listOf(c_tags, c_txn, c_uid, c_txn_next)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        """gin (naksha_tags($c_tags, $c_flags)) WHERE naksha_tags($c_tags, $c_flags) IS NOT NULL""",
                        table, unique = false, addFillFactor = false
                    )
                ).close()
            }
        }

        /**
         * A two-dimensional [SP-GIST](https://www.postgresql.org/docs/current/spgist.html) index above `naksha_ref_point(`[PgColumn.ref_point]`)`.
         */
        @JvmField
        @JsStatic
        val ref_point = def(PgIndex::class, "ref") { self ->
            self.name = "ref_point"
            self.columns = listOf(c_ref_point)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        """spgist (naksha_ref_point($c_ref_point, $c_flags)) WHERE naksha_ref_point($c_ref_point, $c_flags) IS NOT NULL""",
                        table, unique = false, addFillFactor = true
                    )
                ).close()
            }
        }

        /**
         * A two-dimensional [GIST](https://www.postgresql.org/docs/current/gist.html) index above `naksha_geometry(`[PgColumn.geo], [PgColumn.flags]`)`.
         */
        @JvmField
        @JsStatic
        val gist_geo_2d = def(PgIndex::class, "g2d") { self ->
            self.name = "gist_geo_2d"
            self.columns = listOf(c_geo)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        """gist (naksha_2d($c_geo, $c_flags)) WHERE naksha_2d($c_geo, $c_flags) IS NOT NULL""",
                        table, unique = false, addFillFactor = true
                    )
                ).close()
            }
        }

        /**
         * A three-dimensional [GIST](https://www.postgresql.org/docs/current/gist.html) index above `naksha_geometry(`[PgColumn.geo], [PgColumn.flags]`)`.
         */
        @JvmField
        @JsStatic
        val gist_geo_3d = def(PgIndex::class, "g3d") { self ->
            self.name = "gist_geo_3d"
            self.columns = listOf(c_geo)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        """gist (naksha_3d($c_geo, $c_flags)) WHERE naksha_3d($c_geo, $c_flags) IS NOT NULL""",
                        table, unique = false, addFillFactor = true
                    )
                ).close()
            }
        }

        /**
         * A four-dimensional [GIST](https://www.postgresql.org/docs/current/gist.html) index above `naksha_geometry(`[PgColumn.geo], [PgColumn.flags]`)`.
         */
        @JvmField
        @JsStatic
        val gist_geo_4d = def(PgIndex::class, "g4d") { self ->
            self.name = "gist_geo_4d"
            self.columns = listOf(c_geo)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        """gist (naksha_4d($c_geo, $c_flags)) WHERE naksha_4d($c_geo, $c_flags) IS NOT NULL""",
                        table, unique = false, addFillFactor = true
                    )
                ).close()
            }
        }

        /**
         * A two-dimensional [GIST](https://www.postgresql.org/docs/current/gist.html) index above `naksha_geometry(`[PgColumn.geo], [PgColumn.flags]`)`.
         */
        @JvmField
        @JsStatic
        val spgist_geo_2d = def(PgIndex::class, "s2d") { self ->
            self.name = "spgist_geo_2d"
            self.columns = listOf(c_geo)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        """spgist (naksha_2d($c_geo, $c_flags)) WHERE naksha_2d($c_geo, $c_flags) IS NOT NULL""",
                        table, unique = false, addFillFactor = true
                    )
                ).close()
            }
        }

        /**
         * A three-dimensional [GIST](https://www.postgresql.org/docs/current/gist.html) index above `naksha_geometry(`[PgColumn.geo], [PgColumn.flags]`)`.
         */
        @JvmField
        @JsStatic
        val spgist_geo_3d = def(PgIndex::class, "s3d") { self ->
            self.name = "spgist_geo_3d"
            self.columns = listOf(c_geo)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        """spgist (naksha_3d($c_geo, $c_flags)) WHERE naksha_3d($c_geo, $c_flags) IS NOT NULL""",
                        table, unique = false, addFillFactor = true
                    )
                ).close()
            }
        }

        /**
         * A four-dimensional [GIST](https://www.postgresql.org/docs/current/gist.html) index above `naksha_geometry(`[PgColumn.geo], [PgColumn.flags]`)`.
         */
        @JvmField
        @JsStatic
        val spgist_geo_4d = def(PgIndex::class, "s4d") { self ->
            self.name = "spgist_geo_4d"
            self.columns = listOf(c_geo)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        """spgist (naksha_4d($c_geo, $c_flags)) WHERE naksha_4d($c_geo, $c_flags) IS NOT NULL""",
                        table, unique = false, addFillFactor = true
                    )
                ).close()
            }
        }

        /**
         * Index above [PgColumn.ft], [PgColumn.txn] and [PgColumn.txn_next].
         */
        @JvmField
        @JsStatic
        val feature_type = def(PgIndex::class, "ft") { self ->
            self.name = "feature_type"
            self.columns = listOf(c_ft, c_txn, c_txn_next)
            self.naturalOrder = listOf(DESCENDING, DESCENDING, DESCENDING)
            self.includes = listOf(c_tn)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        "btree ($c_ft text_pattern_ops DESC, $c_id text_pattern_ops DESC, $c_txn DESC, $c_uid DESC, $c_txn_next DESC) INCLUDE ($c_tn) WHERE $c_ft IS NOT NULL",
                        table, unique = false, addFillFactor = true
                    )
                ).close()
            }
        }

        /**
         * Index above [PgColumn.cv0], [PgColumn.txn] and [PgColumn.txn_next].
         */
        @JvmField
        @JsStatic
        val cv0 = def(PgIndex::class, "cv0") { self ->
            self.name = "cv0"
            self.columns = listOf(c_cv0, c_txn, c_txn_next)
            self.naturalOrder = listOf(DESCENDING, DESCENDING, DESCENDING)
            self.includes = listOf(c_tn)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        "btree ($c_cv0 DESC, $c_txn DESC, $c_txn_next DESC) INCLUDE ($c_tn) WHERE $c_cv0 IS NOT NULL",
                        table, unique = false, addFillFactor = true
                    )
                ).close()
            }
        }

        /**
         * Index above [PgColumn.cv1], [PgColumn.txn] and [PgColumn.txn_next].
         */
        @JvmField
        @JsStatic
        val cv1 = def(PgIndex::class, "cv1") { self ->
            self.name = "cv1"
            self.columns = listOf(c_cv1, c_txn, c_txn_next)
            self.naturalOrder = listOf(DESCENDING, DESCENDING, DESCENDING)
            self.includes = listOf(c_tn)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        "btree ($c_cv1 DESC, $c_txn DESC, $c_txn_next DESC) INCLUDE ($c_tn) WHERE $c_cv1 IS NOT NULL",
                        table, unique = false, addFillFactor = true
                    )
                ).close()
            }
        }

        /**
         * Index above [PgColumn.cv2], [PgColumn.txn] and [PgColumn.txn_next].
         */
        @JvmField
        @JsStatic
        val cv2 = def(PgIndex::class, "cv2") { self ->
            self.name = "cv2"
            self.columns = listOf(c_cv2, c_txn, c_txn_next)
            self.naturalOrder = listOf(DESCENDING, DESCENDING, DESCENDING)
            self.includes = listOf(c_tn)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        "btree ($c_cv2 DESC, $c_txn DESC, $c_txn_next DESC) INCLUDE ($c_tn) WHERE $c_cv2 IS NOT NULL",
                        table, unique = false, addFillFactor = true
                    )
                ).close()
            }
        }

        /**
         * Index above [PgColumn.cv3], [PgColumn.txn] and [PgColumn.txn_next].
         */
        @JvmField
        @JsStatic
        val cv3 = def(PgIndex::class, "cv3") { self ->
            self.name = "cv3"
            self.columns = listOf(c_cv3, c_txn, c_txn_next)
            self.naturalOrder = listOf(DESCENDING, DESCENDING, DESCENDING)
            self.includes = listOf(c_tn)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        "btree ($c_cv3 DESC, $c_txn DESC, $c_txn_next DESC) INCLUDE ($c_tn) WHERE $c_cv3 IS NOT NULL",
                        table, unique = false, addFillFactor = true
                    )
                ).close()
            }
        }

        /**
         * Index above [PgColumn.cs0], [PgColumn.txn] and [PgColumn.txn_next].
         */
        @JvmField
        @JsStatic
        val cs0 = def(PgIndex::class, "cs0") { self ->
            self.name = "cs0"
            self.columns = listOf(c_cs0, c_txn, c_txn_next)
            self.naturalOrder = listOf(DESCENDING, DESCENDING, DESCENDING)
            self.includes = listOf(c_tn)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        "btree ($c_cs0 DESC, $c_txn DESC, $c_txn_next DESC) INCLUDE ($c_tn) WHERE $c_cs0 IS NOT NULL",
                        table, unique = false, addFillFactor = true
                    )
                ).close()
            }
        }

        /**
         * Index above [PgColumn.cs1], [PgColumn.txn] and [PgColumn.txn_next].
         */
        @JvmField
        @JsStatic
        val cs1 = def(PgIndex::class, "cs1") { self ->
            self.name = "cs1"
            self.columns = listOf(c_cs1, c_txn, c_txn_next)
            self.naturalOrder = listOf(DESCENDING, DESCENDING, DESCENDING)
            self.includes = listOf(c_tn)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        "btree ($c_cs1 DESC, $c_txn DESC, $c_txn_next DESC) INCLUDE ($c_tn) WHERE $c_cs1 IS NOT NULL",
                        table, unique = false, addFillFactor = true
                    )
                ).close()
            }
        }

        /**
         * Index above [PgColumn.cs2], [PgColumn.txn] and [PgColumn.txn_next].
         */
        @JvmField
        @JsStatic
        val cs2 = def(PgIndex::class, "cs2") { self ->
            self.name = "cs2"
            self.columns = listOf(c_cs2, c_txn, c_txn_next)
            self.naturalOrder = listOf(DESCENDING, DESCENDING, DESCENDING)
            self.includes = listOf(c_tn)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        "btree ($c_cs2 DESC, $c_txn DESC, $c_txn_next DESC) INCLUDE ($c_tn) WHERE $c_cs2 IS NOT NULL",
                        table, unique = false, addFillFactor = true
                    )
                ).close()
            }
        }

        /**
         * Index above [PgColumn.cs3], [PgColumn.txn] and [PgColumn.txn_next].
         */
        @JvmField
        @JsStatic
        val cs3 = def(PgIndex::class, "cs3") { self ->
            self.name = "cs3"
            self.columns = listOf(c_cs3, c_txn, c_txn_next)
            self.naturalOrder = listOf(DESCENDING, DESCENDING, DESCENDING)
            self.includes = listOf(c_tn)
            self.createFn = Fx2 { conn, table ->
                conn.execute(
                    self.sql(
                        "btree ($c_cs3 DESC, $c_txn DESC, $c_txn_next DESC) INCLUDE ($c_tn) WHERE $c_cs3 IS NOT NULL",
                        table, unique = false, addFillFactor = true
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
            feature_type,
            cv0, cv1, cv2, cv3,
            cs0, cs1, cs2, cs3,
            ref_point,
            gist_geo_2d,
        )
    }

    private var _name: String? = null

    /**
     * The official index name to be used in [naksha.model.objects.NakshaCollection.indices].
     * @since 3.0.0
     */
    var name: String
        get() = _name ?: text
        private set(value) {
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
     * The columns (in order) which are part of the index.
     *
     * This is only informational purpose, because the index can be much more complicated, for example it could be a partial index, and it does not contain columns only included in the index, see [includes].
     */
    var columns: List<PgColumn> = emptyList()
        private set

    /**
     * The natural sort order of the index, should hold one entry for each one in [columns].
     *
     * **Note**: If the sort-order is empty, but [columns] is not, this means that the index does not support sorting, e.g. `GIN` or `GIST` indices.
     */
    var naturalOrder: List<SortOrder> = emptyList()
        private set

    /**
     * The columns being included only in the index.
     */
    var includes: List<PgColumn> = emptyList()
        private set

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
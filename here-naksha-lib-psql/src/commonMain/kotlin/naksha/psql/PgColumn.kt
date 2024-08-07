@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.JsEnum
import naksha.model.request.query.TupleColumn
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass

/**
 * A column descriptor for database columns, especially helpful when reading columns using `SELECT * FROM table`.
 */
@JsExport
class PgColumn : JsEnum() {
    private fun checkThatNotDefined(column: PgColumn) {
        check(!isDefined) { "Changing ${column.name} is not allowed!" }
    }

    private var _i: Int = -1

    /**
     * The index in the columns list of a Naksha standard table. A value from 0 to n-1 if this column is part of Naksha standard tables, with n being the amount of columns. A value of `-1`, if this column is no standard column.
     */
    var i: Int
        get() = _i
        set(value) {
            checkThatNotDefined(this)
            _i = value
        }

    /**
     * The name of the column.
     */
    val name: String
        get() = super.text

    private var _type: PgType? = null

    /**
     * The type of the column.
     */
    var type: PgType
        get() {
            return _type ?: PgType.NULL
        }
        set(value) {
            checkThatNotDefined(this)
            _type = value
        }

    private var _extra: String? = null

    /**
     * Optional extras for the definition, for example "NOT NULL".
     */
    var extra: String?
        get() = _extra
        set(value) {
            checkThatNotDefined(this)
            _extra = value
        }

    /**
     * The stringified version, the same as returned by [toString].
     */
    var sqlDefinition: String = ""
        get() {
            // TODO: KotlinCompilerBug - How can field ever be null?
            if (field.isNullOrEmpty()) {
                field = "$name $type" + if (extra != null) " $extra" else ""
            }
            return field
        }
        private set

    companion object PgColumnCompanion {
        /**
         * Returns the columns instance for the given name,
         * @param columnName the column name.
         * @return the column enumeration value.
         */
        @JvmStatic
        @JsStatic
        fun of(columnName: String): PgColumn = get(columnName, PgColumn::class)

        /**
         * The [storage-number][naksha.model.StoreNumber] of the [tuple][naksha.model.Tuple], which describes where the [tuple][naksha.model.Tuple] is stored within the storage.
         */
        @JvmField
        @JsStatic
        val store_number = def(PgColumn::class, "store_number") { self ->
            self._i = 0
            self._type = PgType.INT64
            self._extra = "NOT NULL"
        }

        /**
         * The epoch timestamp in millisecond when the [tuple][naksha.model.Tuple] was produced, which is the last time the feature was modified.
         */
        @JvmField
        @JsStatic
        val updated_at = def(PgColumn::class, "updated_at") { self ->
            self._i = 1
            self._type = PgType.INT64
            self._extra = "NOT NULL"
        }

        /**
         * The epoch timestamp in millisecond when the [feature][naksha.model.objects.NakshaFeature] was originally created. If the value is _null_, this means this [tuple][naksha.model.Tuple] is the initial state of the [feature][naksha.model.objects.NakshaFeature], so the value is the same as [updated_at].
         */
        @JvmField
        @JsStatic
        val created_at = def(PgColumn::class, "created_at") { self ->
            self._i = 2
            self._type = PgType.INT64
        }

        /**
         * The epoch timestamp in millisecond when the [feature][naksha.model.objects.NakshaFeature] was modified last by the author. If the value is _null_, this means this [tuple][naksha.model.Tuple] was changed by the author, so the value is the same as [updated_at].
         */
        @JvmField
        @JsStatic
        val author_ts = def(PgColumn::class, "author_ts") { self ->
            self._i = 3
            self._type = PgType.INT64
        }

        /**
         * If this is the latest [tuple][naksha.model.Tuple] (state) of the [feature][naksha.model.objects.NakshaFeature], the value is _null_; otherwise it stores the next [version][naksha.model.Version] (aka transaction) of the next [tuple][naksha.model.Tuple].
         */
        @JvmField
        @JsStatic
        val txn_next = def(PgColumn::class, "txn_next") { self ->
            self._i = 4
            self._type = PgType.INT64
        }

        /**
         * The [version][naksha.model.Version] (aka transaction) of this [tuple][naksha.model.Tuple] (state).
         */
        @JvmField
        @JsStatic
        val txn = def(PgColumn::class, "txn") { self ->
            self._i = 5
            self._type = PgType.INT64
            self._extra = "NOT NULL"
        }

        /**
         * If this is not the first [tuple][naksha.model.Tuple] (state) of the [feature][naksha.model.objects.NakshaFeature], this stores the previous [version][naksha.model.Version] (aka transaction) of the [feature][naksha.model.objects.NakshaFeature]; otherwise it is _null_ (if this is the first [tuple][naksha.model.Tuple]).
         */
        @JvmField
        @JsStatic
        val ptxn = def(PgColumn::class, "ptxn") { self ->
            self._i = 6
            self._type = PgType.INT64
        }

        /**
         * The unique identifier of this [tuple][naksha.model.Tuple] (state) within the [version][naksha.model.Version] (aka transaction) of this [feature][naksha.model.objects.NakshaFeature].
         */
        @JvmField
        @JsStatic
        val uid = def(PgColumn::class, "uid") { self ->
            self._i = 7
            self._type = PgType.INT
            self._extra = "NOT NULL"
        }

        /**
         * The unique identifier within the previous [version][naksha.model.Version] (aka transaction) of this [feature][naksha.model.objects.NakshaFeature].
         */
        @JvmField
        @JsStatic
        val puid = def(PgColumn::class, "puid") { self ->
            self._i = 8
            self._type = PgType.INT
        }

        /**
         * The amount of changes that have been done so far to the [feature][naksha.model.objects.NakshaFeature], _1_ for the initial [tuple][naksha.model.Tuple], every new [tuple][naksha.model.Tuple] has a value incremented by one.
         */
        @JvmField
        @JsStatic
        val change_count = def(PgColumn::class, "change_count") { self ->
            self._i = 9
            self._type = PgType.INT
            self._extra = "NOT NULL DEFAULT 1"
        }

        /**
         * The unique hash of this [tuple][naksha.model.Tuple] (state), calculated by the storage using the static [Metadata.hash][naksha.model.Metadata.hash] method.
         */
        @JvmField
        @JsStatic
        val hash = def(PgColumn::class, "hash") { self ->
            self._i = 10
            self._type = PgType.INT
            self._extra = "NOT NULL"
        }

        /**
         * The binary [HERE tile-key][naksha.geo.HereTile.intKey] of the [reference-point][naksha.model.Tuple.referencePoint] of the [tuple][naksha.model.Tuple] (state). This is calculated using the static [Metadata.geoGrid][naksha.model.Metadata.geoGrid] method.
         */
        @JvmField
        @JsStatic
        val geo_grid = def(PgColumn::class, "geo_grid") { self ->
            self._i = 11
            self._type = PgType.INT
            self._extra = "NOT NULL"
        }

        /**
         * Flags.
         * ```
         *  Reserved       PN       R0  AE   TE     FE    GE
         * [0000-0000]-[0000-0000]-[00][00][0000]-[0000][0000]
         * ```
         * - GE: geometry (and reference point) encoding - bits: 0-3
         * - FE: feature encoding - bits: 4-7
         * - TE: tags encoding - bits: 8-11
         * - AE: action - bits: 12+13
         * - R0: reserved - bit: 14-15
         * - PN: partition number - bits: 16-23
         * - ---
         * - Reserved - bits: 24-31
         *
         * Possible actions are:
         * - [CREATED][naksha.model.ActionValues.CREATED]
         * - [UPDATED][naksha.model.ActionValues.UPDATED]
         * - [DELETED][naksha.model.ActionValues.DELETED]
         */
        @JvmField
        @JsStatic
        val flags = def(PgColumn::class, "flags") { self ->
            self._i = 12
            self._type = PgType.INT
            self._extra = "NOT NULL"
        }

        /**
         * An always generated special column, being a 160-bit number, storing the [store_number], [txn], and [uid] as one binary.
         *
         * This column simplifies indexing of tuples.
         */
        @JvmField
        @JsStatic
        val tuple_number = def(PgColumn::class, "tuple_number") { self ->
            self._i = 13
            self._type = PgType.BYTE_ARRAY
            self._extra = "STORAGE PLAIN GENERATED ALWAYS AS (int8send(store_number)||int8send(txn)||int4send(uid)) STORED NOT NULL"
        }

        /**
         * The feature-id.
         */
        @JvmField
        @JsStatic
        val id = def(PgColumn::class, "id") { self ->
            self._i = 14
            self._type = PgType.STRING
            self._extra = "STORAGE PLAIN COLLATE \"C\""
        }

        /**
         * The application-id of the application that produced a [tuple][naksha.model.Tuple].
         */
        @JvmField
        @JsStatic
        val app_id = def(PgColumn::class, "app_id") { self ->
            self._i = 15
            self._type = PgType.STRING
            self._extra = "STORAGE PLAIN NOT NULL COLLATE \"C\""
        }

        /**
         * The author that takes ownership for the [tuple][naksha.model.Tuple].
         */
        @JvmField
        @JsStatic
        val author = def(PgColumn::class, "author") { self ->
            self._i = 16
            self._type = PgType.STRING
            self._extra = "STORAGE PLAIN COLLATE \"C\""
        }

        /**
         * The [type][naksha.model.objects.NakshaFeature.type] of the [feature][naksha.model.objects.NakshaFeature], _null_ if it matches the [default-type of the collection][naksha.model.objects.NakshaCollection.defaultType].
         */
        @JvmField
        @JsStatic
        val type = def(PgColumn::class, "type") { self ->
            self._i = 17
            self._type = PgType.STRING
            self._extra = "STORAGE PLAIN COLLATE \"C\""
        }

        /**
         * If this [tuple][naksha.model.Tuple] is a fork of the [feature][naksha.model.objects.NakshaFeature] from a different origin, this column stores the [GUID][naksha.model.Guid] of the origin [tuple][naksha.model.Tuple]. This happens, when the feature moves from one storage into another, from one map into another, from one collection into another or just changes its id.
         */
        @JvmField
        @JsStatic
        val origin = def(PgColumn::class, "origin") { self ->
            self._i = 18
            self._type = PgType.STRING
            self._extra = "STORAGE PLAIN COLLATE \"C\""
        }

        /**
         * The [tags][naksha.model.TagMap] of the [tuple][naksha.model.Tuple], stored as map.
         */
        @JvmField
        @JsStatic
        val tags = def(PgColumn::class, "tags") { self ->
            self._i = 19
            self._type = PgType.BYTE_ARRAY
            self._extra = "STORAGE EXTERNAL"
        }

        /**
         * The reference-point of the [feature][naksha.model.objects.NakshaFeature].
         */
        @JvmField
        @JsStatic
        val ref_point = def(PgColumn::class, "ref_point") { self ->
            self._i = 20
            self._type = PgType.BYTE_ARRAY
            self._extra = "STORAGE EXTERNAL"
        }

        /**
         * The geometry of the [feature][naksha.model.objects.NakshaFeature].
         */
        @JvmField
        @JsStatic
        val geo = def(PgColumn::class, "geo") { self ->
            self._i = 21
            self._type = PgType.BYTE_ARRAY
            self._extra = "STORAGE EXTERNAL"
        }

        /**
         * The serialized [feature][naksha.model.objects.NakshaFeature].
         */
        @JvmField
        @JsStatic
        val feature = def(PgColumn::class, "feature") { self ->
            self._i = 22
            self._type = PgType.BYTE_ARRAY
            self._extra = "STORAGE EXTERNAL"
        }

        /**
         * An arbitrary binary attachment.
         */
        @JvmField
        @JsStatic
        val attachment = def(PgColumn::class, "attachment") { self ->
            self._i = 23
            self._type = PgType.BYTE_ARRAY
            self._extra = "STORAGE EXTENDED"
        }

        /**
         * All columns being used with Naksha.
         *
         * - **PLAIN** prevents either compression or out-of-line storage.
         * - **EXTENDED** allows both compression and out-of-line storage.
         * - **EXTERNAL** allows out-of-line storage but not compression.
         * - **MAIN** allows compression but not out-of-line storage. Actually, out-of-line storage will still be performed for such columns, but only as a last resort when there is no other way to make the row small enough to fit on a page.
         *
         * The TOAST code will compress and/or move field values out-of-line until the row value is shorter than TOAST_TUPLE_TARGET bytes.
         *
         * **Note**: We order the columns by intention like this to minimize the storage size. The _bytea_ columns will be GZIP compressed on demand by the client storing the data (see [naksha.model.Flags]). Note that in some cases this is not useful, for example, when a `TWKB` geometry or reference-point is given, it is often so small, that compression would increase the size. The general rule is, that anything being smaller than 100 byte, should not be compressed.
         *
         * See [storage-toast.html](https://www.postgresql.org/docs/current/storage-toast.html).
         */
        @JvmField
        @JsStatic
        val allColumns = arrayOf(
            store_number, updated_at, created_at, author_ts, txn_next, txn, ptxn,
            uid, puid, change_count, hash, geo_grid, flags,
            tuple_number, id, app_id, author, type, origin,
            tags, ref_point, geo, feature, attachment
        )

        /**
         * All columns to generate [naksha.model.Metadata], but leaving the details away, so `geometry`, `referencePoint`, `tags`, `feature`, and `attachment`.
         */
        @JvmField
        @JsStatic
        val metaColumn = arrayOf(
            tuple_number, // = store_number, txn, uid
            puid, updated_at, created_at, author_ts, txn_next, ptxn,
            change_count, hash, geo_grid, flags,
            id, app_id, author, type, origin,
        )

        @JvmField
        @JsStatic
        val metaSelect= metaColumn.joinToString(",")

        /**
         * SQL selection of the metadata in binary form, to be used like:
         * ```sql
         * SELECT string_agg($metaSelectToBinary, '\x00'::bytea)
         * FROM {table} WHERE {condition}
         * ```
         */
        @JvmField
        @JsStatic
        val metaSelectToBinary = """($tuple_number
||int4send(coalesce($puid, 0))
||int8send($updated_at)
||int8send(coalesce($created_at, $updated_at))
||int8send(coalesce($author_ts, $updated_at))
||int8send(coalesce($txn_next, 0::bigint))
||int8send(coalesce($ptxn, 0::bigint))
||int4send($change_count)
||int4send($hash)
||int4send($geo_grid)
||int4send($flags)
||$id::bytea||'\x00'::bytea
||$app_id::bytea||'\x00'::bytea
||coalesce($author,'')::bytea||'\x00'::bytea
||coalesce($type,'')::bytea||'\x00'::bytea
||coalesce($origin,'')::bytea||'\x00'::bytea
)""".trimEnd()

        init {
            // This is only self-check code.
            for ((i, col) in allColumns.withIndex()) {
                check(i == col.i) { "Invalid columns, column '${col.name}' should be at index ${col.i}, but found at $i" }
            }
        }

        /**
         * Returns the [PgColumn] that matches to the official [TupleColumn].
         * @param tupleColumn the [TupleColumn] to resolve.
         * @return the [PgColumn] that matches this [TupleColumn]; if any.
         */
        @JvmStatic
        @JsStatic
        fun ofRowColumn(tupleColumn: TupleColumn): PgColumn? = when (tupleColumn.name) {
            TupleColumn.UPDATED_AT -> updated_at
            TupleColumn.CREATED_AT -> created_at
            TupleColumn.AUTHOR_TS -> author_ts
            TupleColumn.NEXT_VERSION -> txn_next
            TupleColumn.VERSION -> txn
            TupleColumn.PREV_VERSION -> ptxn
            TupleColumn.UID -> uid
            TupleColumn.PUID -> puid
            TupleColumn.HASH -> hash
            TupleColumn.CHANGE_COUNT -> change_count
            TupleColumn.GEO_GRID -> geo_grid
            TupleColumn.FLAGS -> flags
            TupleColumn.ID -> id
            TupleColumn.APP_ID -> app_id
            TupleColumn.AUTHOR -> author
            TupleColumn.TYPE -> type
            TupleColumn.TAGS -> tags
            TupleColumn.REF_POINT -> ref_point
            TupleColumn.GEOMETRY -> geo
            TupleColumn.FEATURE -> feature
            // attachment
            else -> null
        }
    }

    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = PgColumn::class
    override fun initClass() {}
}
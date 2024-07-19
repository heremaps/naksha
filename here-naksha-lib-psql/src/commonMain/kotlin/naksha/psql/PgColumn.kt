@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.JsEnum
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
        get() = super.str

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

        @JvmField
        @JsStatic
        val created_at = def(PgColumn::class, "created_at") { self ->
            self._i = 0
            self._type = PgType.INT64
        }

        @JvmField
        @JsStatic
        val updated_at = def(PgColumn::class, "updated_at") { self ->
            self._i = 1
            self._type = PgType.INT64
            self._extra = "NOT NULL"
        }

        @JvmField
        @JsStatic
        val author_ts = def(PgColumn::class, "author_ts") { self ->
            self._i = 2
            self._type = PgType.INT64
        }

        @JvmField
        @JsStatic
        val txn_next = def(PgColumn::class, "txn_next") { self ->
            self._i = 3
            self._type = PgType.INT64
        }

        @JvmField
        @JsStatic
        val txn = def(PgColumn::class, "txn") { self ->
            self._i = 4
            self._type = PgType.INT64
            self._extra = "NOT NULL"
        }

        @JvmField
        @JsStatic
        val uid = def(PgColumn::class, "uid") { self ->
            self._i = 5
            self._type = PgType.INT
        }

        @JvmField
        @JsStatic
        val puid = def(PgColumn::class, "puid") { self ->
            self._i = 6
            self._type = PgType.INT
        }

        @JvmField
        @JsStatic
        val hash = def(PgColumn::class, "hash") { self ->
            self._i = 7
            self._type = PgType.INT
        }

        @JvmField
        @JsStatic
        val change_count = def(PgColumn::class, "change_count") { self ->
            self._i = 8
            self._type = PgType.INT
        }

        @JvmField
        @JsStatic
        val geo_grid = def(PgColumn::class, "geo_grid") { self ->
            self._i = 9
            self._type = PgType.INT
        }

        /**
         * The `flags` encode how data is stored in the `bytea` type columns.
         *
         * ```
         *       Reserved         R1  AE   TE    FE    GE
         * [0000-0000-0000-0000]-[00][00][0000][0000][0000]
         * ```
         * - GE: [geometry][geo] and [reference point][geo_ref] encoding - bits: 0-3
         * - FE: [feature] encoding - bits: 4-7
         * - TE: [tags] encoding - bits: 8-11
         * - AE: action - bits: 12+13
         * - R1: reserved - bits: 14+15
         * - ---
         * - Reserved - bits: 16-31
         *
         * Possible actions are:
         * - [naksha.model.Action.CREATED]
         * - [naksha.model.Action.UPDATED]
         * - [naksha.model.Action.DELETED]
         */
        @JvmField
        @JsStatic
        val flags = def(PgColumn::class, "flags") { self ->
            self._i = 10
            self._type = PgType.INT
        }

        @JvmField
        @JsStatic
        val id = def(PgColumn::class, "id") { self ->
            self._i = 11
            self._type = PgType.STRING
        }

        @JvmField
        @JsStatic
        val origin = def(PgColumn::class, "origin") { self ->
            self._i = 12
            self._type = PgType.STRING
            self._extra = "STORAGE PLAIN COLLATE \"C\""
        }

        @JvmField
        @JsStatic
        val app_id = def(PgColumn::class, "app_id") { self ->
            self._i = 13
            self._type = PgType.STRING
            self._extra = "STORAGE PLAIN NOT NULL COLLATE \"C\""
        }

        @JvmField
        @JsStatic
        val author = def(PgColumn::class, "author") { self ->
            self._i = 14
            self._type = PgType.STRING
            self._extra = "STORAGE PLAIN COLLATE \"C\""
        }

        @JvmField
        @JsStatic
        val type = def(PgColumn::class, "type") { self ->
            self._i = 15
            self._type = PgType.STRING
            self._extra = "STORAGE PLAIN COLLATE \"C\""
        }

        @JvmField
        @JsStatic
        val tags = def(PgColumn::class, "tags") { self ->
            self._i = 16
            self._type = PgType.BYTE_ARRAY
            self._extra = "STORAGE PLAIN"
        }

        @JvmField
        @JsStatic
        val geo_ref = def(PgColumn::class, "geo_ref") { self ->
            self._i = 17
            self._type = PgType.BYTE_ARRAY
            self._extra = "STORAGE PLAIN"
        }

        @JvmField
        @JsStatic
        val geo = def(PgColumn::class, "geo") { self ->
            self._i = 18
            self._type = PgType.BYTE_ARRAY
            self._extra = "STORAGE EXTERNAL"
        }

        @JvmField
        @JsStatic
        val feature = def(PgColumn::class, "feature") { self ->
            self._i = 19
            self._type = PgType.BYTE_ARRAY
            self._extra = "STORAGE EXTERNAL"
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
         * **Note**: We order the columns by intention like this to minimize the storage size. The bytea columns will always be GZIP compressed (see [naksha.model.Flags]).
         *
         * See [storage-toast.html](https://www.postgresql.org/docs/current/storage-toast.html).
         */
        //
        @JvmField
        @JsStatic
        val columns = arrayOf(
            created_at, updated_at, author_ts, txn_next, txn,
            uid, puid, hash, change_count, geo_grid, flags,
            id, origin, app_id, author, type,
            tags, geo_ref, geo, feature
        )

        init {
            // This is only self-check code.
            var i = 0
            for (col in columns) {
                check(col.i == i++) { "Invalid table state, column '${col.name}' should be at index ${col.i}, but found at $i" }
            }
        }
    }

    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = PgColumn::class
    override fun initClass() {}
}
package naksha.psql

import naksha.base.Int64
import naksha.base.Platform.PlatformCompanion.toJSON
import naksha.jbon.IBook
import naksha.model.*
import naksha.psql.PgColumn.PgColumnCompanion.allColumns

/**
* Thin [IBook] wrapper that maps column names to row values from [PgColumnRows].
     *
     * This implements [IBook] by delegating index/name lookups to the underlying [rows]
 * instance so that [Tuple] getters can resolve members at read time.
 * @since 3.0
 */
internal class PgRowDict(
    val row: Int,
    private val rows: PgColumnRows,
    val fn: Int64,
    val dataEncoding: DataEncoding,
) : IBook {

    override val id: String?
        get() {
            if (fn >= Int64(0)) {
                return rows.getString(row, PgColumn.id)
            }
            return rows.getString(row, PgColumn.id)
        }

    override val length: Int
        get() = rows.columns.size

    override fun get(index: Int): Any? {
        val col = rows.columns.getOrNull(index) ?: return null
        return col.values.getOrNull(row)
    }

    override fun indexOfString(string: String): Int {
        val col = rows.columnByName[string]
        return col?.index ?: -1
    }

    override fun getStringAt(index: Int): String? {
        val col = rows.columns.getOrNull(index) ?: return null
        val v = col.values.getOrNull(row)
        return if (v is String) v else null
    }

    override fun hasNames(): Boolean = true

    override fun indexOfName(name: String): Int = indexOfString(name)

    override fun getNameAt(index: Int): String? = rows.columns.getOrNull(index)?.name

    override fun namesLength(): Int = rows.columns.size

    override fun getByName(name: String): Any? {
        val col = rows.columnByName[name] ?: return null
        return col.values.getOrNull(row)
    }

    /**
     * Returns the value of a `jsonb` column as raw JSON text.
     */
    private fun getJsonText(column: PgColumn): String? {
        val value = rows.getAny(row, column)
        return when (value) {
            null -> null
            is String -> value
            else -> toJSON(value)
        }
    }

    override fun getAllWithHash(hash: Int): List<naksha.jbon.DictEntry> = emptyList()
}

/**
 * Helper class to convert rows into arrays of column and vice versa. The main purpose is to read and write full tuples, but it supports basically as well virtual columns.
 * @since 3.0
 */
internal class PgColumnRows {
    /**
     * All columns being added already.
     * @since 3.0
     */
    val columns = mutableListOf<PgColumnEntry>()
    internal val columnByName = mutableMapOf<String, PgColumnEntry>()
    private var isComplete: Boolean? = null
    private var names: String? = null
    private var namesAggregate: String? = null
    private var placeholders: String? = null
    private var arrayTypeNames: Array<String>? = null
    private fun clearCache(): PgColumnRows {
        isComplete = null
        names = null
        namesAggregate = null
        placeholders = null
        arrayTypeNames = null
        return this
    }

    /**
     * The amount of rows.
     * @since 3.0
     */
    var size: Int = 0
        set(value) {
            if (field != value) {
                for (column in columns) {
                    column.values.size = value
                }
                field = value
            }
        }

    fun withMinSize(size: Int): PgColumnRows {
        if (this.size < size) this.size = size
        return this
    }

    /**
     * If all rows are coming from the same storage, the storage-number of it.
     * @since 3.0
     */
    var storageNumber: Int64? = null

    /**
     * @see [storageNumber]
     */
    fun withStorageNumber(value: Int64): PgColumnRows {
        storageNumber = value
        return this
    }

    /**
     * If all rows are coming from the same map, the map-number of it.
     * @since 3.0
     */
    var mapNumber: Int? = null

    /**
     * @see [mapNumber]
     */
    fun withMapNumber(value: Int): PgColumnRows {
        mapNumber = value
        return this
    }

    /**
     * If all rows are coming from the same collection, the collection-number of it.
     * @since 3.0
     */
    var collectionNumber: Int? = null

    /**
     * The feature encoding that applies to every tuple in this result set.
     *
     * The encoding is no longer persisted as a per-row column; it is a per-collection setting
     * looked up by callers (e.g. from [PgCollection.head.dataEncoding]) and passed here so the
     * synthesized [Tuple.dataEncoding] tells decoders how to unpack the feature bytes.
     *
     * **MUST be set via [withDefaultDataEncoding] before any call to [getTuple] / [get]** —
     * leaving it unset and then materializing a tuple would silently decode features under the
     * wrong encoding, so [getTuple] throws instead of guessing.
     * @since 3.0
     */
    var defaultDataEncoding: DataEncoding? = null

    /**
     * @see [defaultDataEncoding]
     */
    fun withDefaultDataEncoding(value: DataEncoding): PgColumnRows {
        defaultDataEncoding = value
        return this
    }

    /**
     * @see [collectionNumber]
     */
    fun withCollectionNumber(value: Int): PgColumnRows {
        collectionNumber = value
        return this
    }

    /**
     * Tests if tuple read from these rows will be complete.
     * @since 3.0
     */
    val complete: Boolean
        get() {
            val c = isComplete
            if (c != null) return c
            var detected = true
            for (pgColumn in allColumns) {
                if (!hasColumn(pgColumn)) {
                    detected = false
                    break
                }
            }
            isComplete = detected
            return detected
        }

    fun addColumn(col: PgColumn): PgColumnRows = addColumn(col.name, col.type)

    fun addColumn(name: String, type: PgType): PgColumnRows {
        clearCache()
        val existing = columnByName[name]
        if (existing == null) {
            val column = PgColumnEntry(columns.size, name, type).withSize(size)
            columns.add(column)
            columnByName[column.name] = column
        }
        return this
    }

    fun addColumns(cols: List<PgColumn>): PgColumnRows {
        clearCache()
        var i = columns.size
        for (col in cols) {
            val existing = columnByName[col.name]
            if (existing == null) {
                val column = PgColumnEntry(col, i++).withSize(size)
                columns.add(column)
                columnByName[column.name] = column
            }
        }
        return this
    }
    fun getColumn(name: String): PgColumnEntry? = columnByName[name]
    fun getColumn(col: PgColumn): PgColumnEntry? = columnByName[col.name]
    fun getColumn(index: Int): PgColumnEntry? = if (index in 0 until columns.size) columns[index] else null
    fun hasColumn(name: String): Boolean = getColumn(name) != null
    fun hasColumn(col: PgColumn): Boolean = getColumn(col) != null
    fun hasColumn(index: Int): Boolean = getColumn(index) != null


    fun getAny(row: Int, columnName: String): Any? = columnByName[columnName]?.values?.get(row)
    fun getAny(row: Int, column: PgColumn): Any? = getAny(row, column.name)
    fun getInt(row: Int, columnName: String): Int? {
        val value = getAny(row, columnName)
        return if (value is Int) value else null
    }
    fun getInt(row: Int, column: PgColumn): Int? = getInt(row, column.name)
    fun getInt64(row: Int, columnName: String): Int64? {
        val value = getAny(row, columnName)
        return if (value is Int64) value else null
    }
    fun getInt64(row: Int, column: PgColumn): Int64? = getInt64(row, column.name)
    fun getDouble(row: Int, columnName: String): Double? {
        val value = getAny(row, columnName)
        return if (value is Double) value else null
    }
    fun getDouble(row: Int, column: PgColumn): Double? = getDouble(row, column.name)
    fun getString(row: Int, columnName: String): String? {
        val value = getAny(row, columnName)
        return if (value is String) value else null
    }
    fun getString(row: Int, column: PgColumn): String? = getString(row, column.name)
    fun getByteArray(row: Int, columnName: String): ByteArray? {
        val value = getAny(row, columnName)
        return if (value is ByteArray) value else null
    }
    fun getByteArray(row: Int, column: PgColumn): ByteArray? = getByteArray(row, column.name)
    fun getB64(row: Int, column: PgColumn, featureNumber: Int64): TupleNumber? = getB64(row, column.name, featureNumber)
    fun getB64(row: Int, columnName: String, featureNumber: Int64): TupleNumber? {
        val raw = getByteArray(row, columnName) ?: return null
        val storageNumber = this.storageNumber ?: return null
        val mapNumber = this.mapNumber ?: return null
        val collectionNumber = this.collectionNumber ?: return null
        return try {
            TupleNumber.fromB64(raw, storageNumber, mapNumber, collectionNumber, featureNumber)
        } catch (_: Exception) {
            null
        }
    }
    fun getB128(row: Int, column: PgColumn): TupleNumber? = getB128(row, column.name)
    fun getB128(row: Int, columnName: String): TupleNumber? {
        val raw = getByteArray(row, columnName) ?: return null
        val storageNumber = this.storageNumber ?: return null
        val mapNumber = this.mapNumber ?: return null
        val collectionNumber = this.collectionNumber ?: return null
        return try {
            TupleNumber.fromB128(raw, storageNumber, mapNumber, collectionNumber)
        } catch (_: Exception) {
            null
        }
    }
    fun getTuple(row: Int, storageNumber: Int64, mapNumber: Int, collectionNumber: Int): Tuple? {
        if (row < 0 || row >= size) return null
        val fn = getInt64(row, PgColumn.fn) ?: return null
        val version = getInt64(row, PgColumn.version) ?: return null
        val nextVersion = getInt64(row, PgColumn.next_version)
        val encoding = defaultDataEncoding ?: throw illegalState(
            "PgColumnRows.defaultDataEncoding was not set before calling getTuple(); " +
                    "set it from the collection's dataEncoding via withDefaultDataEncoding(...)."
        )
        val members = PgRowDict(
            row = row,
            rows = this,
            fn = fn,
            dataEncoding = encoding
        )
        return Tuple(
            storageNumber = storageNumber,
            mapNumber = mapNumber,
            collectionNumber = collectionNumber,
            featureNumber = fn,
            version = naksha.model.Version(version),
            nextVersion = nextVersion ?: Int64(-1L),
            membersBook = members,
            jbonBytes = getByteArray(row, PgColumn.feature)
        )
    }

    /**
     * Returns the value of a `jsonb` column as raw JSON text.
     *
     * `jsonb` columns come back from the cursor pre-parsed (a [naksha.base.PlatformMap] /
     * [naksha.base.PlatformList]) since most callers want a typed object. For carriers that hold
     * the raw JSON text (like tags) we round-trip through [toJSON] to recover it.
     */
    private fun getJsonText(row: Int, column: PgColumn): String? {
        val value = getAny(row, column)
        return when (value) {
            null -> null
            is String -> value
            else -> toJSON(value)
        }
    }

    operator fun get(row: Int): Tuple? {
        val storage_num = storageNumber ?: return null
        val map_num = mapNumber ?: return null
        val col_num = collectionNumber ?: return null
        return getTuple(row, storage_num, map_num, col_num)
    }

    fun set(row: Int, columnName: String, value: Any?): Boolean {
        val column = getColumn(columnName)
        if (column != null) {
            withMinSize(row)
            column.values[row] = value
            return true
        }
        return false
    }
    fun set(row: Int, column: PgColumn, value: Any?): Boolean = set(row, column.name, value)
    /**
     * Adds one [PgColumnEntry] per declared [naksha.model.objects.Member].
     *
     * Idempotent — built-in names are never re-added by addColumns(allColumns), and members are checked individually.
     */
    fun addCustomMembers(members: naksha.model.objects.MemberList?): PgColumnRows {
        if (members == null) return this
        for (m in members) {
            if (m == null) continue
            addColumn(PgCustomMemberValues.pgColumnName(m.name), PgCustomMemberValues.pgTypeFor(m.dataType))
        }
        return this
    }

    /**
     * Populates the [Member][naksha.model.objects.Member] columns for the given row by walking the [feature] using each member's [path][naksha.model.objects.Member.effectivePath] and coercing the value to the SQL type.
     *
     * Missing keys and mismatched types both produce a NULL column value. Mismatches additionally emit a warning via [naksha.base.Platform.PlatformCompanion.logger].
     */
    fun setCustomMembers(row: Int, feature: naksha.model.objects.NakshaFeature?, members: naksha.model.objects.MemberList?) {
        if (feature == null || members == null) return
        for (m in members) {
            if (m == null) continue
            val raw = PgCustomMemberValues.walkFeature(feature, m.effectivePath())
            val coerced = PgCustomMemberValues.coerce(raw, m.dataType, feature.id, m.name)
            set(row, PgCustomMemberValues.pgColumnName(m.name), coerced)
        }
    }

    operator fun set(row: Int, tuple: Tuple) {
        withMinSize(row)
        val members = tuple.membersBook ?: return
        set(row, PgColumn.updated_at, members.getByName("updated_at") as? Int64)
        set(row, PgColumn.created_at, members.getByName("created_at") as? Int64)
        set(row, PgColumn.author_ts, members.getByName("author_ts") as? Int64)
        set(row, PgColumn.cv0, members.getByName("cv0") as? Double)
        set(row, PgColumn.cv1, members.getByName("cv1") as? Double)
        set(row, PgColumn.cv2, members.getByName("cv2") as? Double)
        set(row, PgColumn.cv3, members.getByName("cv3") as? Double)
        set(row, PgColumn.hash, members.getByName("hash") as? Int)
        set(row, PgColumn.here_tile, members.getByName("here_tile") as? Int)
        set(row, PgColumn.cc, members.getByName("cc") as? Int)
        val fn = tuple.featureNumber
        set(row, PgColumn.fn, fn)
        set(row, PgColumn.version, tuple.version.txn)
        set(row, PgColumn.next_version, tuple.nextVersion)
        set(row, PgColumn.base_tn, members.getByName("base_tn") as? ByteArray)
        set(row, PgColumn.id, if (fn >= Int64(0)) null else members.getByName("id") as? String)
        set(row, PgColumn.app_id, members.getByName("app_id") as? String)
        set(row, PgColumn.author, members.getByName("author") as? String)
        set(row, PgColumn.origin, members.getByName("origin") as? String)
        set(row, PgColumn.target, members.getByName("target") as? String)
        set(row, PgColumn.ft, members.getByName("ft") as? String)
        set(row, PgColumn.cs0, members.getByName("cs0") as? String)
        set(row, PgColumn.cs1, members.getByName("cs1") as? String)
        set(row, PgColumn.cs2, members.getByName("cs2") as? String)
        set(row, PgColumn.cs3, members.getByName("cs3") as? String)
        set(row, PgColumn.tags, members.getByName("tags") as? String)
        set(row, PgColumn.ref_point, members.getByName("ref_point") as? ByteArray)
        set(row, PgColumn.feature, tuple.jbonBytes)
        set(row, PgColumn.geo, members.getByName("geo") as? ByteArray)
        set(row, PgColumn.attachment, members.getByName("attachment") as? ByteArray)
    }

    operator fun set(row: Int, cursor: PgCursor) {
        withMinSize(row)
        for (column in columns) {
            if (cursor.contains(column.name)) {
                val value = cursor.column(column.name)
                column.values[row] = value
            }
        }
    }

    /**
     * Add the current row of the cursor.
     * @param cursor the cursor from which to read.
     * @return `true` if a rows was read; `false` if the cursor is not at a valid row.
     * @since 3.0
     */
    fun add(cursor: PgCursor): Boolean {
        if (cursor.isRow()) {
            val row = size
            size += 1
            for (column in columns) {
                if (cursor.contains(column.name)) {
                    val value = cursor.column(column.name)
                    column.values[row] = value
                }
            }
            return true
        }
        return false
    }

    /**
     * Read all rows from cursor, expects the cursor to be at first result, usage:
     * ```kotlin
     * plan.execute(queryValues).fetch().use { resultRows.addAll(it) }
     * ```
     * @since 3.0
     */
    fun addAll(cursor: PgCursor): PgColumnRows {
        while (add(cursor)) cursor.next()
        return this
    }

    /**
     * Read all rows from cursor, expects the cursor to be at first result and that for each column, there is an array of values, so an aggregate generated via `ARRAY_AGG`.
     * @since 3.0
     */
    fun addAggregated(cursor: PgCursor): PgColumnRows {
        if (cursor.isRow()) {
            for (column in columns) {
                if (cursor.contains(column.name)) {
                    val values = cursor.column(column.name)
                    if (values is Array<*>) {
                        withMinSize(values.size)
                        for (i in 0 until values.size) {
                            set(i, column.name, values[i])
                        }
                    }
                }
            }
        }
        return this
    }

    /**
     * Returns the names of all columns as comma separated string.
     * @return the names of all columns as comma separated string.
     * @since 3.0
     */
    fun names(): String {
        val cached = this.names
        if (cached != null) return cached
        val names = columns.joinToString(", ") { PgUtil.quoteIdent(it.name) }
        this.names = names
        return names
    }

    /**
     * Returns the names of all columns as comma separated string, surrounded with aggregation instruction, _(like `ARRAY_AGG(id)`)_, usage:
     *
     * ```kotlin
     * val rows = PgColumnRows().addColumns(allColumns)
     * val SQL = """SELECT ${rows.namesAggregate()}
     * FROM "naksha~admin".${collections.head.quotedName}
     * WHERE id = ANY($1)"""
     * val plan = conn.prepare(SQL, rows.typeNames())
     * val cursor = plan.execute(rows.valuesExecutable())
     * ```
     *
     * @return the names of all columns as comma separated string, surrounded with aggregation instruction.
     * @since 3.0
     */
    fun namesAggregate(): String {
        val cached = this.namesAggregate
        if (cached != null) return cached
        val names = columns.joinToString(", ") {
            val q = PgUtil.quoteIdent(it.name)
            "ARRAY_AGG($q) AS $q"
        }
        this.namesAggregate = names
        return names
    }

    /**
     * Returns the placeholders of all columns as comma separated string _(&dollar;1, &dollar;2, ...)_, usage:
     *
     * ```sql
     * WITH new_row AS (
     *   SELECT * FROM UNNEST(${rows.placeholders()})
     *   AS t(${rows.names()})
     * )
     * INSERT INTO ${collection.head.quotedName} (${rows.names()})
     * SELECT * FROM new_row
     * ```
     *
     * @return the placeholders of all columns as comma separated string.
     * @since 3.0
     */
    fun placeholders(): String {
        val cached = this.placeholders
        if (cached != null) return cached
        val placeholders = columns.joinToString(", ") { "\$${(it.index + 1)}" }
        this.placeholders = placeholders
        return placeholders
    }

    /**
     * Returns the array type-names of all columns, for example, when the type is [PgType.INT64], it will return `int8[]`, usage:
     *
     * ```kotlin
     * val sql = """WITH new_row AS (
     *   SELECT * FROM UNNEST(${rows.placeholders()})
     *   AS t(${rows.names()})
     * )
     * INSERT INTO ${collection.head.quotedName} (${rows.names()})
     * SELECT * FROM new_row"""
     * val plan = conn.prepare(sql, rows.typeNames())
     * ```
     * @return the array type-names of all columns.
     * @since 3.0
     */
    fun typeNames(): Array<String> = Array(columns.size) { columns[it].type.text + "[]" }

    /**
     * Returns the values of all columns cast to a type that is supported by [PgPlan.execute], usage:
     *
     * ```kotlin
     * val sql = """WITH new_row AS (
     *   SELECT * FROM UNNEST(${rows.placeholders()})
     *   AS t(${rows.names()})
     * )
     * INSERT INTO ${collection.head.quotedName} (${rows.names()})
     * SELECT * FROM new_row"""
     * val plan = conn.prepare(sql, rows.typeNames())
     * val cursor = plan.execute(rows.valuesExecutable())
     * ```
     * Beware that the array really is two-dimensional: `Array<Array<Any?>>`.
     * @return the values of all columns as `Array<Any?>`.
     * @since 3.0
     */
    @Suppress("UNCHECKED_CAST")
    fun values(): Array<Any?> = Array(columns.size) { columns[it].anyArray() } as Array<Any?>
}
package naksha.psql

import naksha.base.Int64
import naksha.model.*
import naksha.model.TupleNumberVariant.TupleNumberVariant_C.B128
import naksha.model.TupleNumberVariant.TupleNumberVariant_C.B64
import naksha.psql.PgColumn.PgColumnCompanion.allColumns

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
    private val columnByName = mutableMapOf<String, PgColumnEntry>()
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
     * The encoding flags that apply to every tuple in this result set.
     *
     * `flags` is no longer persisted as a per-row column; the encoding is a per-collection setting
     * looked up by callers (e.g. from [PgCollection.head.defaultFlags]) and passed here so the
     * synthesized [Metadata.flags] tells decoders which feature encoding to use.
     *
     * **MUST be set via [withDefaultFlags] before any call to [getTuple] / [get]** — leaving it
     * unset and then materializing a tuple would silently decode features under the wrong
     * encoding (default 0 = JBON-no-gzip), so [getTuple] throws instead of guessing.
     * @since 3.0
     */
    var defaultFlags: Flags? = null

    /**
     * @see [defaultFlags]
     */
    fun withDefaultFlags(value: Flags): PgColumnRows {
        defaultFlags = value
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
        val tupleNumber = TupleNumber(storageNumber, mapNumber, collectionNumber, fn, naksha.model.Version(version))
        val base_tn = getByteArray(row, PgColumn.base_tn)
        val baseTupleNumber = if (base_tn != null) TupleNumber.fromByteArray(base_tn, 0, B128, storageNumber, mapNumber, collectionNumber) else null
        val nextVersion = getInt64(row, PgColumn.next_version)
        val flags = defaultFlags ?: throw illegalState(
            "PgColumnRows.defaultFlags was not set before calling getTuple(); " +
                    "synthesized Metadata.flags would be 0 and decoders would silently mis-handle the " +
                    "collection's feature encoding. Set defaultFlags from the collection's defaultFlags " +
                    "(or Naksha.DEFAULT_FLAGS for admin reads) via withDefaultFlags(...)."
        )
        val meta = Metadata(
            tupleNumber = tupleNumber,
            flags = flags,
            changeCount = getInt(row, PgColumn.cc) ?: 1,
            updatedAt = getInt64(row, PgColumn.updated_at) ?: return null,
            createdAt = getInt64(row, PgColumn.created_at),
            authorTs = getInt64(row, PgColumn.author_ts),
            nextVersion = nextVersion,
            baseTupleNumber = baseTupleNumber,
            hash = getInt(row, PgColumn.hash) ?: -1,
            hereTile = getInt(row, PgColumn.here_tile) ?: -1,
            id = getString(row, PgColumn.id) ?: return null,
            appId = getString(row, PgColumn.app_id) ?: return null,
            author = getString(row, PgColumn.author),
            origin = getString(row, PgColumn.origin),
            target = getString(row, PgColumn.target),
            ft = getString(row, PgColumn.ft),
            cs0 = getString(row, PgColumn.cs0),
            cs1 = getString(row, PgColumn.cs1),
            cs2 = getString(row, PgColumn.cs2),
            cs3 = getString(row, PgColumn.cs3),
            cv0 = getDouble(row, PgColumn.cv0),
            cv1 = getDouble(row, PgColumn.cv1),
            cv2 = getDouble(row, PgColumn.cv2),
            cv3 = getDouble(row, PgColumn.cv3),
        )
        return Tuple(
            meta = meta,
            feature = getByteArray(row, PgColumn.feature),
            geo = getByteArray(row, PgColumn.geo),
            referencePoint = getByteArray(row, PgColumn.ref_point),
            tags = getByteArray(row, PgColumn.tags),
            attachment = getByteArray(row, PgColumn.attachment),
            complete = complete
        )
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
     * Adds one [PgColumnEntry] per declared [naksha.model.objects.CustomMember].
     *
     * Idempotent — built-in names are never re-added by addColumns(allColumns), and members are checked individually.
     */
    fun addCustomMembers(members: naksha.model.objects.CustomMemberList?): PgColumnRows {
        if (members == null) return this
        for (m in members) {
            if (m == null) continue
            addColumn(PgCustomMemberValues.pgColumnName(m.name), PgCustomMemberValues.pgTypeFor(m.dataType))
        }
        return this
    }

    /**
     * Populates the [CustomMember][naksha.model.objects.CustomMember] columns for the given row by walking the [feature] using each member's [path][naksha.model.objects.CustomMember.effectivePath] and coercing the value to the SQL type.
     *
     * Missing keys and mismatched types both produce a NULL column value. Mismatches additionally emit a warning via [naksha.base.Platform.PlatformCompanion.logger].
     */
    fun setCustomMembers(row: Int, feature: naksha.model.objects.NakshaFeature?, members: naksha.model.objects.CustomMemberList?) {
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
        val meta = tuple.meta
        set(row, PgColumn.updated_at, meta.updatedAt)
        set(row, PgColumn.created_at, meta.createdAt)
        set(row, PgColumn.author_ts, meta.authorTs)
        set(row, PgColumn.cv0, meta.cv0)
        set(row, PgColumn.cv1, meta.cv1)
        set(row, PgColumn.cv2, meta.cv2)
        set(row, PgColumn.cv3, meta.cv3)
        set(row, PgColumn.hash, meta.hash)
        set(row, PgColumn.here_tile, meta.hereTile)
        set(row, PgColumn.cc, meta.changeCount)
        set(row, PgColumn.fn, meta.tupleNumber.featureNumber)
        set(row, PgColumn.version, meta.tupleNumber.version.txn)
        set(row, PgColumn.next_version, meta.nextVersion)
        set(row, PgColumn.base_tn, meta.baseTupleNumber?.toB128())
        set(row, PgColumn.id, meta.id)
        set(row, PgColumn.app_id, meta.appId)
        set(row, PgColumn.author, meta.author)
        set(row, PgColumn.origin, meta.origin)
        set(row, PgColumn.target, meta.target)
        set(row, PgColumn.ft, meta.ft)
        set(row, PgColumn.cs0, meta.cs0)
        set(row, PgColumn.cs1, meta.cs1)
        set(row, PgColumn.cs2, meta.cs2)
        set(row, PgColumn.cs3, meta.cs3)
        set(row, PgColumn.tags, tuple.tags)
        set(row, PgColumn.ref_point, tuple.referencePoint)
        set(row, PgColumn.feature, tuple.feature)
        set(row, PgColumn.geo, tuple.geo)
        set(row, PgColumn.attachment, tuple.attachment)
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
        val names = columns.joinToString(", ") { it.name }
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
        val names = columns.joinToString(", ") { "ARRAY_AGG(${it.name}) AS ${it.name}" }
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
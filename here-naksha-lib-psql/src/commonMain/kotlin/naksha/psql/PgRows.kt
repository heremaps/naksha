package naksha.psql

import naksha.base.AnyList
import naksha.geo.SpGeometry
import naksha.jbon.BookType
import naksha.jbon.HeapBook
import naksha.model.*
import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.base.NakshaException
import naksha.base.Platform.PlatformCompanion.fromJSON
import naksha.base.Platform.PlatformCompanion.toJSON
import naksha.base.TupleNumber
import naksha.model.objects.MemberType
import naksha.base.Version
import naksha.base.internalError
import naksha.base.proxy
import naksha.geo.GeoUtil
import naksha.model.objects.MemberType.MemberType_C.BOOLEAN
import naksha.model.objects.MemberType.MemberType_C.BYTE_ARRAY
import naksha.model.objects.MemberType.MemberType_C.FLOAT32
import naksha.model.objects.MemberType.MemberType_C.FLOAT64
import naksha.model.objects.MemberType.MemberType_C.INT16
import naksha.model.objects.MemberType.MemberType_C.INT32
import naksha.model.objects.MemberType.MemberType_C.INT64
import naksha.model.objects.MemberType.MemberType_C.INT8
import naksha.model.objects.MemberType.MemberType_C.SPATIAL
import naksha.model.objects.MemberType.MemberType_C.STRING
import naksha.model.objects.MemberType.MemberType_C.TAG_LIST
import naksha.model.objects.MemberType.MemberType_C.TAG_MAP
import naksha.model.objects.MemberType.MemberType_C.TUPLE_NUMBER
import naksha.model.objects.StandardMembers.StandardMembers_C.FeatureBytes
import naksha.model.objects.StandardMembers.StandardMembers_C.Id
import naksha.model.objects.StandardMembers.StandardMembers_C.NextVersion
import naksha.model.objects.StandardMembers.StandardMembers_C.Tn

/**
 * Helper class to convert rows into arrays of column-data and vice versa.
 *
 * The main purpose is to read and write tuples, but it supports basically as well virtual columns. This class translates the correct member [book][naksha.jbon.IBook] _HEAD_ values into what the storage expects. For example, the encoder will place into the member [book][naksha.jbon.IBook] the reference to [TagList] or [TagMap], and this class will know the physical representation. For example a [TagList] need to be encoded as `text[]`, while a [TagMap] need to be encoded as `JSONB`, which effectively is a `JSON` string. This class will do the serialization and deserialization.
 * @since 3.0
 */
internal class PgRows {
    /**
     * All columns being added already.
     * @since 3.0
     */
    val columns = mutableListOf<PgColumnWithValues>()
    private var aliases: String? = null
    private var namesAggregate: String? = null
    private var placeholders: String? = null
    private var arrayTypeNames: Array<String>? = null
    private fun clearCache(): PgRows {
        aliases = null
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

    /**
     * Ensures that all columns have at least this amount of values, if too short, adds `null` values until the minimal size is reached.
     * @since 3.0
     */
    fun setMinRows(rowsCount: Int): PgRows {
        if (this.size < rowsCount) {
            this.size = rowsCount
        }
        return this
    }

    /**
     * When set, clear the [columns], add all columns of the given [PgCollection], and set the [collectionNumber], [catalogNumber], and [databaseNumber]. Eventually this will read all columns, so a full member-book, from a specific database table, no matter if from _HISTORY_ or _HEAD_.
     * @since 3.0
     */
    var collection: PgCollection? = null
        set(collection) {
            if (collection != null) {
                clearCache()
                columns.clear()
                collectionNumber = collection.collectionNumber
                catalogNumber = collection.catalog.catalogNumber
                databaseNumber = collection.catalog.storage.number
                for (pgColumn in collection.columns) {
                    columns.add(PgColumnWithValues(pgColumn))
                }
            }
            field = collection
        }

    /**
     * Clear the members, then add all members of the given [PgCollection] to the row-set.
     * @param col the [PgCollection] of which to add the members.
     * @return this
     * @see [collection]
     * @since 3.0
     */
    fun withCollection(col: PgCollection): PgRows {
        this.collection = col
        return this
    }

    /**
     * If all rows are coming from the same storage, the storage-number of it.
     * @since 3.0
     */
    var databaseNumber: Long? = null
        set(value) {
            collection = null
            field = value
        }

    /**
     * @see [databaseNumber]
     */
    fun withDatabaseNumber(value: Long): PgRows {
        databaseNumber = value
        return this
    }

    /**
     * If all rows are coming from the same catalog, the catalog-number of it.
     * @since 3.0
     */
    var catalogNumber: Int? = null
        set(value) {
            collection = null
            field = value
        }

    /**
     * @see [catalogNumber]
     */
    fun withCatalogNumber(value: Int): PgRows {
        catalogNumber = value
        return this
    }

    /**
     * If all rows are coming from the same collection, the collection-number of it.
     * @since 3.0
     */
    var collectionNumber: Int? = null
        set(value) {
            collection = null
            field = value
        }

    /**
     * @see [collectionNumber]
     */
    fun withCollectionNumber(value: Int): PgRows {
        collectionNumber = value
        return this
    }

    fun addColumns(columns: Array<PgColumn>): PgRows {
        clearCache()
        for (column in columns) {
            val alias = column.name
            val existing = getColumn(alias)
            if (existing != null) continue
            this.columns.add(PgColumnWithValues(column, alias).withSize(size))
        }
        return this
    }

    fun addColumn(column: PgColumn, alias: String = column.name): PgRows {
        clearCache()
        val existing = getColumn(alias)
        if (existing == null) {
            val column = PgColumnWithValues(column, alias).withSize(size)
            columns.add(column)
        }
        return this
    }
    fun addColumn(alias: String, type: MemberType): PgRows {
        clearCache()
        val existing = getColumn(alias)
        if (existing == null) {
            val column = PgColumnWithValues(PgColumn(-1, alias, type)).withSize(size)
            columns.add(column)
        }
        return this
    }

    fun getColumn(alias: String): PgColumnWithValues? {
        for (column in columns) if (column.alias == alias) return column
        return null
    }
    fun getColumn(index: Int): PgColumnWithValues? = if (index in 0 until columns.size) columns[index] else null
    fun hasColumn(alias: String): Boolean = getColumn(alias) != null
    fun hasColumn(index: Int): Boolean = getColumn(index) != null


    fun getAny(row: Int, alias: String): Any? = getColumn(alias)?.values?.get(row)
    fun getAny(row: Int, column: PgColumn): Any? = getAny(row, column.name)
    fun getInt(row: Int, alias: String): Int? = getAny(row, alias) as? Int
    fun getInt(row: Int, column: PgColumn): Int? = getInt(row, column.name)
    fun getLong(row: Int, alias: String): Long? = getAny(row, alias) as Long?
    fun getLong(row: Int, column: PgColumn): Long? = getLong(row, column.name)
    fun getDouble(row: Int, alias: String): Double? = getAny(row, alias) as Double?
    fun getDouble(row: Int, column: PgColumn): Double? = getDouble(row, column.name)
    fun getString(row: Int, alias: String): String? = getAny(row, alias) as String?
    fun getString(row: Int, column: PgColumn): String? = getString(row, column.name)
    fun getByteArray(row: Int, alias: String): ByteArray? = getAny(row, alias) as ByteArray?
    fun getByteArray(row: Int, column: PgColumn): ByteArray? = getByteArray(row, column.name)
    fun getArray(row: Int, alias: String): Array<*>? = getAny(row, alias) as Array<*>?
    fun getArray(row: Int, column: PgColumn): Array<*>? = getArray(row, column.name)
    fun getSpatial(row: Int, alias: String): SpGeometry? {
        // Stored as bytea, therefore returned by JDBC as ByteArray.
        val raw = getByteArray(row, alias) ?: return null
        return try {
            Naksha.decodeGeometry(raw)
        } catch (_: Exception) {
            null
        }
    }
    fun getTagMap(row: Int, alias: String): TagMap? {
        // Stored as JSONB, therefore returned by JDBC as string.
        val raw = getString(row, alias) ?: return null
        return try {
            fromJSON(raw).proxy(TagMap::class)
        } catch (_: Exception) {
            null
        }
    }
    fun getTagList(row: Int, alias: String): TagList? {
        // Stored as text[], therefore returned by JDBC as Array.
        val raw = getArray(row, alias) ?: return null
        return try {
            val tagList = TagList()
            tagList.setCapacity(raw.size)
            for (tag in raw) {
                if (tag !is String) continue
                // We know that we read back data we serialized, tags are normalized!
                tagList.addTag(tag, false)
            }
            tagList
        } catch (_: Exception) {
            null
        }
    }
    fun getB64(row: Int, columnName: String, featureNumber: Long): TupleNumber? {
        val raw = getByteArray(row, columnName) ?: return null
        val storageNumber = this.databaseNumber ?: return null
        val mapNumber = this.catalogNumber ?: return null
        val collectionNumber = this.collectionNumber ?: return null
        return try {
            TupleNumber.fromB64(raw, storageNumber, mapNumber, collectionNumber, featureNumber)
        } catch (_: Exception) {
            null
        }
    }
    fun getB128(row: Int, columnName: String): TupleNumber? {
        val raw = getByteArray(row, columnName) ?: return null
        val storageNumber = this.databaseNumber ?: return null
        val mapNumber = this.catalogNumber ?: return null
        val collectionNumber = this.collectionNumber ?: return null
        return try {
            TupleNumber.fromB128(raw, storageNumber, mapNumber, collectionNumber)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Read the given row into a tuple, requires that a [collection] is assigned.
     * @param row the row number.
     * @return the [Tuple] extracted from the row or `null`, if either no [collection] set, the row number is outside the result-set, or something else failed.
     * @since 3.0
     */
    fun getTuple(row: Int): Tuple? {
        if (row !in 0..< size) return null
        val collection = this.collection ?: return null
        // Rebuild the members-book in the same canonical member order as Tuple.encodeFeature
        // (collection.useMembers()) so the blob's positional member references resolve; the
        // tuple-number is reconstructed from the split `_fn`/`_version` columns.
        val members = collection.head.useMembers()
        val membersBook = HeapBook(BookType.MEMBER_BOOK)
        var featureBytes: ByteArray? = null
        for (i in 0 until members.size) {
            val member = members[i] ?: continue
            val name = member.name
            if (name == FeatureBytes.name) {
                val value = getColumn(name)?.values?.get(row)
                if (value !is ByteArray) throw NakshaException(ILLEGAL_STATE, "The feature root is no byte-array")
                featureBytes = value
                continue
            }
            if (member.isVirtual()) continue
            when (name) {
                Tn.name -> {
                    val fn = getColumn(PgColumn.FnColumn.name)?.values?.get(row) as? Long
                    val ver = getColumn(PgColumn.VersionColumn.name)?.values?.get(row) as? Long
                    val db = databaseNumber; val cat = catalogNumber; val col = collectionNumber
                    val tn = if (fn != null && ver != null && db != null && cat != null && col != null)
                        TupleNumber(db, cat, col, fn, ver) else null
                    membersBook.put(name, tn)
                }
                NextVersion.name -> {
                    val raw = getColumn(name)?.values?.get(row) as? Long
                    membersBook.put(name, raw ?: Version.HEAD.number)
                }
                else -> {
                    val pg_value = getColumn(name)?.values?.get(row)
                    val value = member.dataType.convert(pg_value)
                    membersBook.put(name, value)
                }
            }
        }
        if (featureBytes == null) throw NakshaException(ILLEGAL_STATE, "Missing mandatory member '${FeatureBytes.name}'!")
        return Tuple(featureBytes = featureBytes, membersBook = membersBook)
    }

    private fun toAnyListOrNull(raw: Any?): Any? {
        if (raw == null) return null
        val list = AnyList()
        when (raw) {
            is Array<*> -> for (e in raw) list.add(e)
            is Iterable<*> -> for (e in raw) list.add(e)
            else -> return raw
        }
        return list
    }

    operator fun get(row: Int): Tuple? = getTuple(row)

    // TODO: The current usage luckily avoids setting structured types with special encoding.
    //       However, theoretically we need to handle TagMap and TagList!
    fun setColumn(row: Int, columnName: String, value: Any?): Boolean {
        val column = getColumn(columnName)
        if (column != null) {
            setMinRows(row+1)
            column.values[row] = if (SPATIAL == column.pgColumn.memberType) GeoUtil.toTWKB(value as SpGeometry?) else value
            return true
        }
        return false
    }

    operator fun set(row: Int, tuple: Tuple) = setRow(row, tuple)

    fun setRow(row: Int, tuple: Tuple) {
        setMinRows(row+1)
        val membersBook = tuple.membersBook
        val END = membersBook.namesLength()
        for (i in 0 until END) {
            val memberName = membersBook.getNameAt(i) ?: continue
            val column = getColumn(memberName) ?: continue
            val member_value = membersBook[memberName]
            // Note: The members book is required to contain correctly typed values.
            // We convert these values into what is stored in PostgresQL.
            column.values[row] = when (val member_type = column.pgColumn.memberType) {
                BOOLEAN -> member_value as? Boolean
                INT8 -> member_value as? Byte
                INT16 -> member_value as? Short
                INT32 -> member_value as? Byte
                INT64 -> member_value as? Long
                FLOAT32 -> member_value as? Float
                FLOAT64 -> member_value as? Double
                STRING -> member_value as? String
                BYTE_ARRAY -> member_value as? ByteArray
                // Stored as bytea
                TUPLE_NUMBER -> (member_value as? TupleNumber)?.toB256()
                // Stored as TWKB bytea
                SPATIAL -> if (member_value is SpGeometry) GeoUtil.toTWKB(member_value) else null
                // Stored as JSONB string
                TAG_MAP -> if (member_value is TagMap) toJSON(member_value) else null
                // Stored as text[]
                TAG_LIST -> if (member_value is TagList) member_value.toStringArray(true) else null
                else -> throw internalError("PgRows: Unknown member type in member book: $member_type")
            }
        }
        // The members-book keeps the tuple-number as a single `_tn` entry; the table splits it into the
        // `_fn` and `_version` columns, so populate those from the tuple-number.
        val tn = tuple.tupleNumber
        getColumn(PgColumn.FnColumn.name)?.let { it.values[row] = tn.featureNumber }
        getColumn(PgColumn.VersionColumn.name)?.let { it.values[row] = tn.version }
        // HEAD rows store next_version as NULL; translate the encoder's HEAD sentinel into NULL.
        getColumn(PgColumn.NextVersionColumn.name)?.let { if (it.values[row] == Version.HEAD.number) it.values[row] = null }
        // `_id` is materialized only when it is not derivable from the feature-number: fn < 0 => hashed
        // id, fn >= 0 => id equals fn so `_id` stays NULL (a CHECK enforces both cases).
        getColumn(Id.name)?.let { it.values[row] = if (tn.featureNumber < 0L) membersBook[Id.name] else null }
        val featureColumn = getColumn(FeatureBytes.name) ?: return
        featureColumn.values[row] = tuple.featureBytes
    }

    /**
     * Copies the columns from the cursor to the given position. The method does nothing, if the given cursor is not positioned at a row ([PgCursor.isRow] is _false_).
     * @param row the row-number to set, if the given cursor is at a valid row.
     * @param cursor the cursor to read from.
     * @since 3.0
     */
    operator fun set(row: Int, cursor: PgCursor) {
        if (!cursor.isRow()) return
        // Grow to hold index `row` (size must be row + 1) so appended read rows are retained.
        setMinRows(row + 1)
        val columnNames = cursor.columnNames()
        for (columnName in columnNames) {
            val column = getColumn(columnName) ?: continue
            val value = cursor.column(columnName)
            column.values[row] = value
        }
    }

    /**
     * Read from the given cursor and add a row to the end of the row-set. Requires that the cursor is positioned on a row _([PgCursor.isRow])_. Does not move the cursor forward.
     * @param cursor the cursor from which to read.
     * @return `true` if a row was read; `false` if the cursor is not at a valid row  _([PgCursor.isRow] is _false_).
     * @since 3.0
     */
    fun read(cursor: PgCursor): Boolean {
        if (cursor.isRow()) {
            set(size ,cursor)
            return true
        }
        return false
    }

    /**
     * Read all rows from cursor to the end of the rows, expects the cursor to be positioned at the first row that should be read, usage:
     * ```kotlin
     * val rows = PgRows().withCollection(pgCollection)
     * plan.execute(query).fetch().use { rows.readAll(it) }
     * // Process the rows
     * ```
     * @since 3.0
     */
    fun readAll(cursor: PgCursor): PgRows {
        while (read(cursor)) cursor.next()
        return this
    }

    /**
     * Returns the aliases of all columns as comma separated string, optionally quoted.
     * @return the aliases of all columns as comma separated string.
     * @since 3.0
     */
    fun aliases(): String {
        var aliases = this.aliases
        if (aliases != null) return aliases
        aliases = columns.joinToString(", ") { PgUtil.quoteIdent(it.alias) }
        this.aliases = aliases
        return aliases
    }

    /**
     * Returns the placeholders of all columns as comma separated string _(&dollar;1, &dollar;2, ...)_, usage:
     *
     * ```kotlin
     * val sql = """WITH new_row AS (
     *   SELECT ${rows.decodedColumns()} FROM UNNEST(${rows.placeholders()})
     *   AS t(${rows.names()})
     * )
     * INSERT INTO ${collection.head.quotedName} (${rows.aliases()})
     * SELECT * FROM new_row"""
     * val plan = conn.prepare(sql, rows.typeNames())
     * val cursor = plan.execute(rows.values())
     * ```
     *
     * @return the placeholders of all columns as comma separated string.
     * @since 3.0
     * @see decodedColumns
     * @see placeholders
     * @see typeNames
     * @see values
     */
    fun placeholders(): String {
        var placeholders = this.placeholders
        if (placeholders != null) return placeholders
        val sb = StringBuilder()
        // Postgres bind parameters are 1-based: emit $1, $2, ... $N (not $0 .. $N-1).
        for (i in 0 ..< columns.size) {
            if (!sb.isEmpty()) sb.append(',')
            sb.append('$').append(i + 1)
        }
        placeholders = sb.toString()
        this.placeholders = placeholders
        return placeholders
    }

    /**
     * Returns the array type-names of all columns, for example, when the type is [PgType.INT64], it will return `int8[]`, usage:
     *
     * ```kotlin
     * val sql = """WITH new_row AS (
     *   SELECT ${rows.decodedColumns()} FROM UNNEST(${rows.placeholders()})
     *   AS t(${rows.names()})
     * )
     * INSERT INTO ${collection.head.quotedName} (${rows.aliases()})
     * SELECT * FROM new_row"""
     * val plan = conn.prepare(sql, rows.typeNames())
     * val cursor = plan.execute(rows.values())
     * ```
     * @return the array type-names of all columns.
     * @since 3.0
     * @see decodedColumns
     * @see placeholders
     * @see typeNames
     * @see values
     */
    fun typeNames(): Array<String> = Array(columns.size) {
        val col = columns[it].pgColumn
        // A text[] column can't ride the batch UNNEST (would be text[][]); carry it as jsonb, converted back in newRowProjection().
        if (col.memberType == TAG_LIST) PgType.JSONB.text + "[]"
        else col.pgType.text + "[]"
    }

    /**
     * Used by [PgWriterInsert], [PgWriterUpdate], and [PgWriterUpsert]. This method returns the columns with some decode hacks when send to the storage as array and decoded using `unnest`.
     * @see decodedColumns
     * @see placeholders
     * @see typeNames
     * @see values
     */
    fun decodedColumns(): String = columns.joinToString(", ") {
        val ident = PgUtil.quoteIdent(it.alias)
        when (it.pgColumn.memberType) {
            // We have serialized the text[] as JSON to workaround UNNEST limits.
            // When selecting from the UNNEST we need to restore the text[] fron the JSON.
            TAG_LIST -> "(CASE WHEN $ident IS NULL THEN NULL ELSE ARRAY(SELECT json_array_elements_text($ident::json)) END)::text[] AS $ident"
            // We serialized to String, but PostgresQL expects JSONB, a simple cast does the trick.
            TAG_MAP -> "$ident::jsonb"
            // The rest is as PostgresQL expects it.
            else -> ident
        }
    }

    /**
     * Returns the values of all columns cast to a type that is supported by [PgPlan.execute], usage:
     *
     * ```kotlin
     * val sql = """WITH new_row AS (
     *   SELECT ${rows.decodedColumns()} FROM UNNEST(${rows.placeholders()})
     *   AS t(${rows.names()})
     * )
     * INSERT INTO ${collection.head.quotedName} (${rows.aliases()})
     * SELECT * FROM new_row"""
     * val plan = conn.prepare(sql, rows.typeNames())
     * val cursor = plan.execute(rows.values())
     * ```
     * Beware that the array really is two-dimensional: `Array<Array<Any?>>`.
     * @return the values of all columns as `Array<Any?>`.
     * @since 3.0
     * @see decodedColumns
     * @see placeholders
     * @see typeNames
     * @see values
     */
    @Suppress("UNCHECKED_CAST")
    fun values(): Array<Any?> = Array(columns.size) { columns[it].toArray() } as Array<Any?>
}

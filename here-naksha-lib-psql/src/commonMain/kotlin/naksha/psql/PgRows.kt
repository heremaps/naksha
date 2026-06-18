package naksha.psql

import naksha.base.Int64
import naksha.jbon.BookType
import naksha.jbon.HeapBook
import naksha.model.*
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.NakshaError.NakshaErrorCompanion.INTERNAL_ERROR
import naksha.model.objects.Member
import naksha.model.objects.NakshaCollection
import naksha.model.objects.StandardMembers

/**
 * Helper class to convert rows into arrays of column-data and vice versa. The main purpose is to read and write full tuples, but it supports basically as well virtual columns.
 * @since 3.0
 */
internal class PgRows {
    /**
     * All columns being added already.
     * @since 3.0
     */
    val columns = mutableListOf<PgColumnWithValues>()
    internal val columnByName = mutableMapOf<String, PgColumnWithValues>()
    private var names: String? = null
    private var namesAggregate: String? = null
    private var placeholders: String? = null
    private var arrayTypeNames: Array<String>? = null
    private fun clearCache(): PgRows {
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

    fun withMinSize(size: Int): PgRows {
        if (this.size < size) this.size = size
        return this
    }

    /**
     * If set to true, then the [StandardMembers.NextVersion] is not added when setting the [collection] or calling [withCollection].
     * @since 3.0
     */
    var isHead: Boolean = false

    /**
     * Disables the [StandardMembers.NextVersion], which does not exist in the _HEAD_ table.
     * @param useHead if the _HEAD_ table is used; defaults to _true_.
     * @since 3.0
     */
    fun useHeadTable(useHead: Boolean = true): PgRows {
        isHead = useHead
        return this
    }

    /**
     * When set, clear the [columns] and add all columns of the given [PgCollection].
     * @since 3.0
     */
    var collection: PgCollection? = null
        set(collection) {
            if (collection != null) {
                clearCache()
                collectionNumber = collection.collectionNumber
                catalogNumber = collection.catalog.catalogNumber
                databaseNumber = collection.catalog.storage.number

                val members = collection.useMembers()
                if (!members.isSortedByIndex()) throw NakshaException(ILLEGAL_ARGUMENT, "The members of the given collection are not sorted by index")
                // We add the internal `~fn` (feature-number) and `~version` first.
                columns.clear()
                var index = 0
                columns.add(PgColumnWithValues(index++, "~fn", PgType.INT64))
                columns.add(PgColumnWithValues(index++, "~version", PgType.INT64))
                for (i in 0 ..< members.size) {
                    val member = members[i] ?: throw NakshaException(INTERNAL_ERROR, "The member at index $i is null; this must not happen")
                    if (i != member.index) throw NakshaException(INTERNAL_ERROR, "The member at index $i has an member-index $index; this must not happen, expected $i")
                    // We store the tuple-number in the "~fn" and "~version" columns!
                    if (StandardMembers.Tn.name == member.name) continue
                    // In the HEAD table there is no next-version!
                    if (isHead && StandardMembers.NextVersion.name == member.name) continue
                    // Everything else as declared.
                    columns.add(PgColumnWithValues(index++, member.name, PgType.ofMemberType(member)))
                }
            }
            field = collection
        }

    /**
     * Add the members of the given [NakshaCollection] to the row-set.
     *
     * The members of the given collection must be sorted by index.
     * @param collection the [NakshaCollection] of which to add the members.
     * @return this
     * @since 3.0
     */
    fun withCollection(collection: NakshaCollection): PgRows {
        this.collection = collection
        return this
    }

    /**
     * If all rows are coming from the same storage, the storage-number of it.
     * @since 3.0
     */
    var databaseNumber: Int64? = null
        set(value) {
            collection = null
            field = value
        }

    /**
     * @see [databaseNumber]
     */
    fun withDatabaseNumber(value: Int64): PgRows {
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

    fun addColumn(name: String, type: PgType): PgRows {
        clearCache()
        val existing = columnByName[name]
        if (existing == null) {
            val column = PgColumnWithValues(columns.size, name, type).withSize(size)
            columns.add(column)
            columnByName[column.name] = column
        }
        return this
    }

    fun getColumn(name: String): PgColumnWithValues? = columnByName[name]
    fun getColumn(index: Int): PgColumnWithValues? = if (index in 0 until columns.size) columns[index] else null
    fun hasColumn(name: String): Boolean = getColumn(name) != null
    fun hasColumn(index: Int): Boolean = getColumn(index) != null


    fun getAny(row: Int, columnName: String): Any? = columnByName[columnName]?.values?.get(row)
    fun getInt(row: Int, columnName: String): Int? {
        val value = getAny(row, columnName)
        return if (value is Int) value else null
    }
    fun getInt64(row: Int, columnName: String): Int64? {
        val value = getAny(row, columnName)
        return if (value is Int64) value else null
    }
    fun getDouble(row: Int, columnName: String): Double? {
        val value = getAny(row, columnName)
        return if (value is Double) value else null
    }
    fun getString(row: Int, columnName: String): String? {
        val value = getAny(row, columnName)
        return if (value is String) value else null
    }
    fun getByteArray(row: Int, columnName: String): ByteArray? {
        val value = getAny(row, columnName)
        return if (value is ByteArray) value else null
    }
    fun getB64(row: Int, columnName: String, featureNumber: Int64): TupleNumber? {
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
        if (row < 0 || row >= size) return null
        val collection = this.collection ?: return null
        val members = collection.members ?: return null
        val membersBook = HeapBook(BookType.MEMBER_BOOK)
        var featureBytes: ByteArray? = null
        for (i in 0 until members.size) {
            val member: Member = members[i] ?: throw NakshaException(ILLEGAL_STATE, "Member #$i of collection ${collection.id} is null")
            val name = member.name
            val column: PgColumnWithValues = getColumn(name) ?: throw NakshaException(ILLEGAL_STATE, "Missing member '$name' at index $i of collection ${collection.id}")
            val value = column.values[row]
            if (StandardMembers.Feature.name == name) {
                // Special case, root feature.
                if (value !is ByteArray) throw NakshaException(ILLEGAL_STATE, "The feature root is no byte-array")
                featureBytes = value
            } else {
                membersBook.put(name, value)
            }
        }
        if (featureBytes == null) throw NakshaException(ILLEGAL_STATE, "Missing mandatory member '${StandardMembers.Feature.name}'!")
        return Tuple(featureBytes = featureBytes, membersBook = membersBook)
    }

    operator fun get(row: Int): Tuple? = getTuple(row)

    fun set(row: Int, columnName: String, value: Any?): Boolean {
        val column = getColumn(columnName)
        if (column != null) {
            withMinSize(row)
            column.values[row] = value
            return true
        }
        return false
    }

    operator fun set(row: Int, tuple: Tuple) {
        withMinSize(row)
        val membersBook = tuple.membersBook
        val END = membersBook.namesLength()
        for (i in 0 until END) {
            val memberName = membersBook.getNameAt(i) ?: continue
            val column = getColumn(memberName) ?: continue
            val value = membersBook[memberName]
            set(row, column.name, value)
        }
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
    fun addAll(cursor: PgCursor): PgRows {
        while (add(cursor)) cursor.next()
        return this
    }

    /**
     * Read all rows from cursor, expects the cursor to be at first result and that for each column, there is an array of values, so an aggregate generated via `ARRAY_AGG`.
     * @since 3.0
     */
    fun addAggregated(cursor: PgCursor): PgRows {
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
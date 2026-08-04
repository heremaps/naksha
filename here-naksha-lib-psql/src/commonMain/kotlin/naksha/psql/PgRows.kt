package naksha.psql

import naksha.base.PAnyArray
import naksha.base.Id
import naksha.geo.SpGeometry
import naksha.jbon.BookType
import naksha.jbon.HeapBook
import naksha.model.*
import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.base.NakshaException
import naksha.base.Base.BaseCompanion.FAL
import naksha.base.Base.BaseCompanion.fal
import naksha.base.TupleNumber
import naksha.model.objects.MemberType
import naksha.base.Version
import naksha.base.illegalArg
import naksha.base.illegalState
import naksha.model.objects.Member
import naksha.model.objects.StandardMembers.StandardMembers_C.FeatureBytesMember
import naksha.model.objects.StandardMembers.StandardMembers_C.FeatureNumberMember
import naksha.model.objects.StandardMembers.StandardMembers_C.IdMember
import naksha.model.objects.StandardMembers.StandardMembers_C.NextVersionMember
import naksha.model.objects.StandardMembers.StandardMembers_C.TnMember
import naksha.model.objects.StandardMembers.StandardMembers_C.VersionMember

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
            for (column in columns) {
                column.values.size = rowsCount
            }
        }
        return this
    }

    /**
     * When set, clear the [columns], add all columns of the given [PgCollection], and set the [collectionId], [catalogId], and [databaseId]. Eventually this will read all columns, so a full member-book, from a specific database table, no matter if from _HISTORY_ or _HEAD_.
     * @since 3.0
     */
    var collection: PgCollection? = null
        set(collection) {
            if (collection != null) {
                clearCache()
                columns.clear()
                collectionId = collection.id
                catalogId = collection.catalog.id
                databaseId = collection.catalog.databaseId
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
    fun withPgCollection(col: PgCollection): PgRows {
        this.collection = col
        return this
    }

    /**
     * If all rows are coming from the same storage, the storage-number of it.
     * @since 3.0
     */
    var databaseId: Id? = null
        set(value) {
            collection = null
            field = value
        }

    /**
     * @see [databaseId]
     */
    fun withDatabaseId(value: Id): PgRows {
        databaseId = value
        return this
    }

    /**
     * If all rows are coming from the same catalog, the catalog-number of it.
     * @since 3.0
     */
    var catalogId: Id? = null
        set(value) {
            collection = null
            field = value
        }

    /**
     * @see [catalogId]
     */
    fun withCatalogId(value: Id): PgRows {
        catalogId = value
        return this
    }

    /**
     * If all rows are coming from the same collection, the collection-number of it.
     * @since 3.0
     */
    var collectionId: Id? = null
        set(value) {
            collection = null
            field = value
        }

    /**
     * @see [collectionId]
     */
    fun withCollectionId(value: Id): PgRows {
        collectionId = value
        return this
    }

    fun addColumns(columns: Array<PgColumn>): PgRows {
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
            setMinRows(size)
        }
        return this
    }
    fun addColumn(alias: String, type: MemberType): PgRows {
        clearCache()
        val existing = getColumn(alias)
        if (existing == null) {
            val column = PgColumnWithValues(PgColumn(-1, alias, type)).withSize(size)
            columns.add(column)
            setMinRows(size)
        }
        return this
    }

    fun getColumn(member: Member): PgColumnWithValues? {
        for (column in columns) if (column.pgColumn.name == member.id) return column
        return null
    }
    fun getColumn(col: PgColumn): PgColumnWithValues? {
        for (column in columns) if (column.pgColumn.name == col.name) return column
        return null
    }
    fun getColumn(nameOrAlias: String): PgColumnWithValues? {
        for (column in columns) if (column.alias == nameOrAlias || column.pgColumn.name == nameOrAlias) return column
        return null
    }
    fun getColumn(index: Int): PgColumnWithValues? = if (index in 0 until columns.size) columns[index] else null
    fun hasColumn(nameOrAlias: String): Boolean = getColumn(nameOrAlias) != null
    fun hasColumn(index: Int): Boolean = getColumn(index) != null


    fun getAny(row: Int, alias: String): Any? = getColumn(alias)?.values?.get(row)
    fun getAny(row: Int, column: PgColumn): Any? = getAny(row, column.name)
    fun getInt(row: Int, alias: String): Int? = getAny(row, alias) as? Int
    fun getInt(row: Int, column: PgColumn): Int? = getInt(row, column.name)
    fun getInt64(row: Int, alias: String): Long? = getAny(row, alias) as Long?
    fun getInt64(row: Int, column: PgColumn): Long? = getInt64(row, column.name)
    fun getDouble(row: Int, alias: String): Double? = getAny(row, alias) as Double?
    fun getDouble(row: Int, column: PgColumn): Double? = getDouble(row, column.name)
    fun getString(row: Int, alias: String): String? = getAny(row, alias) as String?
    fun getString(row: Int, column: PgColumn): String? = getString(row, column.name)
    fun getByteArray(row: Int, alias: String): ByteArray? = getAny(row, alias) as ByteArray?
    fun getByteArray(row: Int, column: PgColumn): ByteArray? = getByteArray(row, column.name)
    fun getSpatial(row: Int, alias: String): SpGeometry? {
        val raw = getByteArray(row, alias) ?: return null
        return try {
            Naksha.decodeGeometry(raw)
        } catch (_: Exception) {
            null
        }
    }
    fun getTags(row: Int, alias: String): TagMap? {
        val raw = getString(row, alias) ?: return null
        return try {
            Naksha.decodeTags(raw)
        } catch (_: Exception) {
            null
        }
    }
    fun getTagList(row: Int, alias: String): TagList? {
        val raw = getString(row, alias) ?: return null
        return try {
            Naksha.decodeTagList(raw)
        } catch (_: Exception) {
            null
        }
    }
    fun getB64(row: Int, columnName: String, featureNumber: Long): TupleNumber? {
        val raw = getByteArray(row, columnName) ?: return null
        val databaseNumber = this.databaseId?.number ?: return null
        val catalogNumber = this.catalogId?.number?.toInt() ?: return null
        val collectionNumber = this.collectionId?.number?.toInt() ?: return null
        return try {
            TupleNumber.fromB64(raw, 0, databaseNumber, catalogNumber, collectionNumber, featureNumber)
        } catch (_: Exception) {
            null
        }
    }
    fun getB128(row: Int, columnName: String): TupleNumber? {
        val raw = getByteArray(row, columnName) ?: return null
        val databaseNumber = this.databaseId?.number ?: return null
        val catalogNumber = this.catalogId?.number?.toInt() ?: return null
        val collectionNumber = this.collectionId?.number?.toInt() ?: return null
        return try {
            TupleNumber.fromB128(raw, 0, databaseNumber, catalogNumber, collectionNumber)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Decode the given row into a tuple; requires that a [collection] is assigned.
     * @param row the row number.
     * @return the decoded [Tuple].
     * @since 3.0
     * @throws NakshaException if decoding failed.
     */
    fun getTuple(row: Int): Tuple {
        if (row !in 0..< size) throw illegalArg("${fal(2)}Invalid row number: $row")

        // This is from where we read.
        val pgCollection = this.collection ?: throw illegalState("${fal(2)}Failed to decode tuple, missing collection assignment")
        val collection = pgCollection.head
        val databaseNumber = collection.databaseId.number
        val catalogNumber = collection.catalogId.intValue
        val collectionNumber = collection.id.intValue

        val idMember = collection.getMember(IdMember) ?: throw illegalState("${fal(2)}Failed to find 'id' member in collection ${collection.id}")
        val idColumn = getColumn(IdMember) ?: throw illegalState("${fal(2)}Failed to find 'id' column in PgRows")
        val id_text = idColumn[row] as? String?

        // Decode tuple-number upfront.
        val fnColumn = getColumn(FeatureNumberMember) ?: throw illegalState("${fal(2)}Failed to find 'fn' column in data")
        val versionColumn = getColumn(VersionMember) ?: throw illegalState("${fal(2)}Failed to find 'version' column in data")
        var featureNumber = fnColumn[row] as? Long? ?: throw illegalState("${fal(2)}No value for 'fn' column in row $row")
        val version = versionColumn[row] as? Long? ?: throw illegalState("${fal(2)}No value for 'version' column in row $row")

        // Restore 'id' and 'tn', which can be tricky.
        val id: Id
        if (featureNumber < 0) {
            if (id_text == null) throw illegalState("${fal(2)}Found negative feature-number with missing 'id' value in row $row")
            if (pgCollection.storesCatalogs || pgCollection.storesCollections) {
                // This will calculate and restore the full 64-bit hash, because what we read form the database was only the 32-bit value!
                id = Id(id_text)
                featureNumber = id.number
            } else {
                id = Id(featureNumber)
            }
        } else {
            id = Id(featureNumber)
        }
        val tn = TupleNumber(databaseNumber, catalogNumber, collectionNumber, featureNumber, version)

        // Rebuild the members-book in the same canonical member order as Tuple.encode
        val memberList = pgCollection.head.useMembers()
        val membersBook = HeapBook(BookType.MEMBER_BOOK)
        var featureBytes: ByteArray? = null
        for (i in 0 until memberList.size) {
            // Skip null members exactly like the encoder's pre-population does, so positions stay aligned.
            val member = memberList[i] ?: continue
            when (val name = member.id) {
                TnMember.id -> membersBook.put(name, tn)
                IdMember.id -> membersBook.put(name, if (id.isNumeric) null else id.text)
                NextVersionMember.id -> {
                    val nextVersion = getColumn(name)?.get(row) as? Long?
                    membersBook.put(name, nextVersion ?: Version.HEAD.number)
                }
                FeatureBytesMember.id -> {
                    val value = getColumn(name)?.values?.get(row)
                    if (value !is ByteArray) throw NakshaException(ILLEGAL_STATE, "The feature root is no byte-array")
                    featureBytes = value
                }
                else -> {
                    if (member.isVirtual()) continue
                    val raw = getColumn(name)?.values?.get(row)
                    val value = if (member.dataType == MemberType.TAG_LIST) toAnyListOrNull(raw) else raw
                    membersBook.put(name, value)
                }
            }
        }
        if (featureBytes == null) throw NakshaException(ILLEGAL_STATE, "Missing mandatory member '${FeatureBytesMember.id}'!")
        return Tuple(featureBytes = featureBytes, membersBook = membersBook)
    }

    /**
     * Encode the given [Tuple] into the row.
     * @param row the row number into which to encode the tuple.
     * @param tuple the tuple to encode.
     * @since 3.0
     */
    fun setTuple(row: Int, tuple: Tuple) {
        setMinRows(row)

        // This is into where we write.
        val pgCollection = this.collection ?: throw illegalState("${fal(2)}Failed to decode tuple, missing collection assignment")
        val collection = pgCollection.head
        val databaseNumber = collection.databaseId.number
        val catalogNumber = collection.catalogId.intValue
        val collectionNumber = collection.id.intValue

        val membersBook = tuple.membersBook
        val END = membersBook.namesLength()
        for (i in 0 until END) {
            val memberName = membersBook.getNameAt(i) ?: throw illegalState("${FAL}Missing name in member-book at index $i")
            // The tuple-number (`tn`) is split into `fn` and `version`.
            if (memberName == TnMember.id) continue
            val column = getColumn(memberName) ?: throw illegalState("${FAL}Missing column definition for member $memberName")
            val value = membersBook[memberName]
            column.values[row] = value
        }
        // The members-book keeps the tuple-number as a single `tn` entry.
        // The physical table in Postgres splits it into the `_fn` and `_version` columns.
        val tn = tuple.tupleNumber
        val fnColumn = getColumn(FeatureNumberMember) ?: throw illegalState("${FAL}Missing column definition for feature-number")
        val versionColumn = getColumn(VersionMember) ?: throw illegalState("${FAL}Missing column definition for version")
        // The tuple-number for catalogs and columns encode 64-bit feature-numbers, but in the storage we only use 32-bit.
        val featureNumber: Long = if (pgCollection.storesCatalogs || pgCollection.storesCollections)
            Id.featureNumberAsInt(tn.featureNumber).toLong()
        else
            tn.featureNumber
        fnColumn.values[row] = featureNumber
        versionColumn.values[row] = tn.version

        // The next version in _HEAD_ is always null, no matter what is encoded in the tuple.
        val nextVersionColumn = getColumn(PgColumn.NextVersionColumn.name) ?: throw illegalState("${FAL}Missing column definition for next-version")
        nextVersionColumn.values[row] = null

        // `id` is materialized only when it is not derivable from the feature-number, so: fn < 0
        // if fn >= 0, then id is the stringified fn and Tuple.encode should have set it to null
        // The storage has a CHECK that enforces both cases!
        val idColumn = getColumn(IdMember.id) ?: throw illegalState("${FAL}Missing column definition for id")
        val id_text = membersBook[IdMember.id] as? String?
        if (featureNumber >= 0 && id_text != null) throw illegalState("${FAL}Invalid tuple encoding, 'id' should be null for positive feature-numbers")
        idColumn.values[row] = id_text

        // The feature column finally receives the JBON binary.
        val featureColumn = getColumn(FeatureBytesMember.id) ?: throw illegalState("${FAL}Missing column definition for feature-bytes")
        featureColumn.values[row] = tuple.featureBytes
    }

    private fun toAnyListOrNull(raw: Any?): Any? {
        if (raw == null) return null
        val list = PAnyArray()
        when (raw) {
            is Array<*> -> for (e in raw) list.add(e)
            is Iterable<*> -> for (e in raw) list.add(e)
            else -> return raw
        }
        return list
    }

    operator fun get(row: Int): Tuple? = getTuple(row)

    fun set(row: Int, columnName: String, value: Any?): Boolean {
        val column = getColumn(columnName)
        if (column != null) {
            setMinRows(row)
            column.values[row] = value
            return true
        }
        return false
    }

    operator fun set(row: Int, tuple: Tuple) {
        setTuple(row, tuple)
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

//    /**
//     * Read all rows from cursor, expects the cursor to be at first result and that for each column, there is an array of values, so an aggregate generated via `ARRAY_AGG`.
//     * @since 3.0
//     */
//    fun addAggregated(cursor: PgCursor): PgRows {
//        if (cursor.isRow()) {
//            for (column in columns) {
//                if (cursor.contains(column.name)) {
//                    val values = cursor.column(column.name)
//                    if (values is Array<*>) {
//                        withMinRows(values.size)
//                        for (i in 0 until values.size) {
//                            set(i, column.name, values[i])
//                        }
//                    }
//                }
//            }
//        }
//        return this
//    }

//    /**
//     * Returns the names of all columns as comma separated string, surrounded with aggregation instruction, _(like `ARRAY_AGG(id)`)_, usage:
//     *
//     * ```kotlin
//     * val rows = PgColumnRows().addColumns(allColumns)
//     * val SQL = """SELECT ${rows.aliasesAggregate()}
//     * FROM "naksha~admin".${collections.head.quotedName}
//     * WHERE id = ANY($1)"""
//     * val plan = conn.prepare(SQL, rows.typeNames())
//     * val cursor = plan.execute(rows.valuesExecutable())
//     * ```
//     *
//     * @return the aliases of all columns as comma separated string, surrounded with aggregation instruction, example:
//     * ```sql
//     * ARRAY_AGG("foo") AS "foo", ARRAY_AGG("bar") AS "bar", ...
//     * ```
//     * @since 3.0
//     */
//    fun aliasesAggregate(): String {
//        val cached = this.namesAggregate
//        if (cached != null) return cached
//        val names = columns.joinToString(", ") {
//            val q = PgUtil.quoteIdent(it.alias)
//            "ARRAY_AGG($q) AS $q"
//        }
//        this.namesAggregate = names
//        return names
//    }

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
     *   SELECT * FROM UNNEST(${rows.placeholders()})
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
     *   SELECT * FROM UNNEST(${rows.placeholders()})
     *   AS t(${rows.names()})
     * )
     * INSERT INTO ${collection.head.quotedName} (${rows.aliases()})
     * SELECT * FROM new_row"""
     * val plan = conn.prepare(sql, rows.typeNames())
     * val cursor = plan.execute(rows.values())
     * ```
     * @return the array type-names of all columns.
     * @since 3.0
     */
    fun typeNames(): Array<String> = Array(columns.size) {
        val col = columns[it].pgColumn
        // A text[] column can't ride the batch UNNEST (would be text[][]); carry it as jsonb, converted back in newRowProjection().
        if (col.memberType == MemberType.TAG_LIST) PgType.JSONB.string + "[]"
        else col.pgType.string + "[]"
    }

    fun newRowProjection(): String = columns.joinToString(", ") {
        val ident = PgUtil.quoteIdent(it.alias)
        if (it.pgColumn.memberType == MemberType.TAG_LIST)
            "CASE WHEN $ident IS NULL THEN NULL ELSE ARRAY(SELECT jsonb_array_elements_text($ident)) END AS $ident"
        else ident
    }

    /**
     * Returns the values of all columns cast to a type that is supported by [PgPlan.execute], usage:
     *
     * ```kotlin
     * val sql = """WITH new_row AS (
     *   SELECT * FROM UNNEST(${rows.placeholders()})
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
     */
    @Suppress("UNCHECKED_CAST")
    fun values(): Array<Any?> = Array(columns.size) { columns[it].anyArray() } as Array<Any?>
}
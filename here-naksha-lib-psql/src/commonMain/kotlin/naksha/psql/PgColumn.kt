@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.JsEnum
import naksha.model.request.query.MetaColumn
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass

/**
 * A column descriptor for database columns, especially helpful when reading columns using `SELECT * FROM table`.
 *
 * The extras are used to add constraints, and the storage-method:
 * - **PLAIN** prevents either compression or out-of-line storage.
 * - **EXTENDED** allows both compression and out-of-line storage.
 * - **EXTERNAL** allows out-of-line storage but not compression.
 * - **MAIN** allows compression but not out-of-line storage. Actually, out-of-line storage will still be performed for such columns, but only as a last resort when there is no other way to make the row small enough to fit on a page.
 *
 * The TOAST code will compress and/or move field values out-of-line until the row value is shorter than TOAST_TUPLE_TARGET bytes.
 *
 * **Note**: We order the columns by intention, to minimize the storage size. The _bytea_ columns will be GZIP compressed on demand by the client storing the data (see [naksha.model.Flags]). Note that in some cases this is not useful, for example, when a `TWKB` geometry or reference-point is given, it is often so small, that compression would increase the size. The general rule is, that anything being smaller than 100 byte, should not be compressed, which only leaves [tags], [feature], and [attachment] as candidates. However, we know that [tags] and [feature] are stored GZIP compressed, therefore the only candidate left is [attachment]. So, we store [tags], and [feature] _EXTERNAL_, and [attachment] _EXTENDED_, everything else as _PLAIN_ to keep it available for fast access.
 *
 * See [storage-toast.html](https://www.postgresql.org/docs/current/storage-toast.html).
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

    private var _ident: String? = null

    /**
     * The SQL quoted identifier.
     *
     * The same result (just slower) can be archived using `PgUtil.quoteIdent(col.name)`.
     * @return the SQL quoted identifier (with optional double quotes).
     */
    val ident: String
        get() {
            var ident = this._ident
            if (ident == null) {
                ident = PgUtil.quoteIdent(this.name)
                this._ident = ident
            }
            return ident
        }

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
         * The epoch timestamp in millisecond when the [tuple][naksha.model.Tuple] was produced, which is the last time the feature was modified.
         */
        @JvmField
        @JsStatic
        val updated_at = def(PgColumn::class, "updated_at") { self ->
            self._i = 0
            self._type = PgType.INT64
            self._extra = "NOT NULL"
        }

        /**
         * The epoch timestamp in millisecond when the [feature][naksha.model.objects.NakshaFeature] was originally created. If the value is _null_, this means this [tuple][naksha.model.Tuple] is the initial state of the [feature][naksha.model.objects.NakshaFeature], so the value is the same as [updated_at].
         */
        @JvmField
        @JsStatic
        val created_at = def(PgColumn::class, "created_at") { self ->
            self._i = 1
            self._type = PgType.INT64
        }

        /**
         * The epoch timestamp in millisecond when the [feature][naksha.model.objects.NakshaFeature] was modified last by the author. If the value is _null_, this means this [tuple][naksha.model.Tuple] was changed by the author, so the value is the same as [updated_at].
         */
        @JvmField
        @JsStatic
        val author_ts = def(PgColumn::class, "author_ts") { self ->
            self._i = 2
            self._type = PgType.INT64
        }

        /**
         * The [version][naksha.model.Version] (aka transaction-number) of this [tuple][naksha.model.Tuple] (state).
         */
        @JvmField
        @JsStatic
        val txn = def(PgColumn::class, "txn") { self ->
            self._i = 3
            self._type = PgType.INT64
            self._extra = "NOT NULL"
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
         * If this has a custom value, otherwise _null_.
         */
        @JvmField
        @JsStatic
        val cv0 = def(PgColumn::class, "cv0") { self ->
            self._i = 5
            self._type = PgType.DOUBLE
        }

        /**
         * If this has a custom value, otherwise _null_.
         */
        @JvmField
        @JsStatic
        val cv1 = def(PgColumn::class, "cv1") { self ->
            self._i = 6
            self._type = PgType.DOUBLE
        }

        /**
         * If this has a custom value, otherwise _null_.
         */
        @JvmField
        @JsStatic
        val cv2 = def(PgColumn::class, "cv2") { self ->
            self._i = 7
            self._type = PgType.DOUBLE
        }

        /**
         * If this has a custom value, otherwise _null_.
         */
        @JvmField
        @JsStatic
        val cv3 = def(PgColumn::class, "cv3") { self ->
            self._i = 8
            self._type = PgType.DOUBLE
        }

        //= 9 * 8 = 71 byte

        /**
         * The [version][naksha.model.Version] (aka transaction) of this [tuple][naksha.model.Tuple] (state).
         */
        @JvmField
        @JsStatic
        val uid = def(PgColumn::class, "uid") { self ->
            self._i = 9
            self._type = PgType.INT
            self._extra = "NOT NULL"
        }

        /**
         * The amount of changes that have been done so far to the [feature][naksha.model.objects.NakshaFeature], _1_ for the initial [tuple][naksha.model.Tuple], every new [tuple][naksha.model.Tuple] has a value incremented by one.
         */
        @JvmField
        @JsStatic
        val change_count = def(PgColumn::class, "change_count") { self ->
            self._i = 10
            self._type = PgType.INT
            self._extra = "NOT NULL DEFAULT 1"
        }

        /**
         * The unique hash of this [tuple][naksha.model.Tuple] (state), calculated by the storage using the static [Metadata.hash][naksha.model.Metadata.calculateHash] method.
         */
        @JvmField
        @JsStatic
        val hash = def(PgColumn::class, "hash") { self ->
            self._i = 11
            self._type = PgType.INT
            self._extra = "NOT NULL"
        }

        /**
         * The binary [HERE tile-key][naksha.geo.HereTile.intKey] of the [reference-point][naksha.model.Tuple.referencePoint] of the [tuple][naksha.model.Tuple] (state). This is calculated using the static [Metadata.geoGrid][naksha.model.Metadata.calculateGeoGrid] method.
         */
        @JvmField
        @JsStatic
        val here_tile = def(PgColumn::class, "here_tile") { self ->
            self._i = 12
            self._type = PgType.INT
            self._extra = "NOT NULL"
        }

        /**
         * [Flags][naksha.model.Flags].
         */
        @JvmField
        @JsStatic
        val flags = def(PgColumn::class, "flags") { self ->
            self._i = 13
            self._type = PgType.INT
            self._extra = "NOT NULL"
        }

        //= 71 + 5 * 4 = 91 byte

        /**
         * The [tuple-number][naksha.model.TupleNumber] of this row in 96-bit encoding.
         *
         * The encoding stores, in order, Big-Endian encoded:
         * - version: 56
         * - partition-number: 8 _(extract via `get_byte(tuple_number, 7)`)_
         * - uid: 32
         */
        @JvmField
        @JsStatic
        val tn = def(PgColumn::class, "tn") { self ->
            self._i = 14
            self._type = PgType.BYTE_ARRAY
            self._extra = "STORAGE PLAIN NOT NULL"
        }

        /**
         * The [previous tuple-number][naksha.model.TupleNumber], using the 96-bit encoding.
         *
         * The encoding stores, in order, Big-Endian encoded:
         * - version: 56
         * - partition-number: 8 _(extract via `get_byte(tuple_number, 7)`)_
         * - uid: 32
         */
        @JvmField
        @JsStatic
        val prev_tn = def(PgColumn::class, "prev_tn") { self ->
            self._i = 15
            self._type = PgType.BYTE_ARRAY
            self._extra = "STORAGE PLAIN"
        }

        /**
         * The [tuple-number][naksha.model.TupleNumber] of the _BASE_ state upon which a [three-way-merge](https://en.wikipedia.org/wiki/Merge_(version_control)#Three-way_merge) was done, using the 96-bit encoding.
         *
         * This value is set, when a client reads the _HEAD_ state of a feature, then modifies it into some _NEW_ state, and tries to save its changes, but meanwhile other clients did the same, and a conflict arises. The changes the client did are based upon an older _HEAD_, which we will name _BASE_, and do not reflect the changes the other clients did meanwhile.
         *
         * In this situation an automatic [three-way-merge](https://en.wikipedia.org/wiki/Merge_(version_control)#Three-way_merge) can be done. The changes of the other clients are calculated as difference between _HEAD_ and _BASE_, and the changes the client did is calculated as difference between _NEW_ and _BASE_. Then the difference are added to a patch. This can fail, if both clients changes the same properties, what will cause a conflict. If successful, the patch is applied to _BASE_ and will produce a new _NEW2_ state, that actually contains the changes of the client plus the changes of the other clients. This _NEW2_ state can be written into the storage, and it can refer via [prev_tn] to the old _HEAD_. This can fail again, if others were faster in doing the same, but is an idempotent operation, and can simply be repeated until either conflicting or solved.
         *
         * To document that this happened, the shared _BASE_ state is referred vai `base_tn`.
         *
         * This technically allows to calculate back, what the client actually modified. For this, the difference between _HEAD_ and _BASE_ is calculated, and then the difference between [prev_tn] and _BASE_ is subtracted, resulting in a patch that can be applied to _BASE_ to receive the original _NEW_ state the client had in memory and wanted to persist. This difference will as well document which properties were changed by the client.
         *
         * The encoding stores, in order, Big-Endian encoded:
         * - version: 56
         * - partition-number: 8 _(extract via `get_byte(tuple_number, 7)`)_
         * - uid: 32
         */
        @JvmField
        @JsStatic
        val base_tn = def(PgColumn::class, "base_tn") { self ->
            self._i = 16
            self._type = PgType.BYTE_ARRAY
            self._extra = "STORAGE PLAIN"
        }

        /**
         * The feature-id.
         */
        @JvmField
        @JsStatic
        val id = def(PgColumn::class, "id") { self ->
            self._i = 17
            self._type = PgType.STRING
            self._extra = "STORAGE PLAIN NOT NULL COLLATE \"C\""
        }

        /**
         * The application-id of the application that produced a [tuple][naksha.model.Tuple].
         */
        @JvmField
        @JsStatic
        val app_id = def(PgColumn::class, "app_id") { self ->
            self._i = 18
            self._type = PgType.STRING
            self._extra = "STORAGE PLAIN NOT NULL COLLATE \"C\""
        }

        /**
         * The author that takes ownership for the [tuple][naksha.model.Tuple].
         */
        @JvmField
        @JsStatic
        val author = def(PgColumn::class, "author") { self ->
            self._i = 19
            self._type = PgType.STRING
            self._extra = "STORAGE PLAIN NOT NULL COLLATE \"C\""
        }

        /**
         * The stringified [Guid][naksha.model.Guid] from where the feature originates.
         *
         * Whenever a feature is copied from a foreign storage, map, or collection, or the feature-id is changed in an update, the `origin` refers to the originating feature.
         *
         * When a client decides to read the _HEAD_ state of a feature, and then to merge a foreign feature logically into it, the client should set `origin` to the foreign feature it merged into this one. This allows to track this, and to rebase the new feature, should the original one being updated.
         *
         * Eventually `origin` is used to perform rebasing. A rebase is a complex [three-way-merge](https://en.wikipedia.org/wiki/Merge_(version_control)#Three-way_merge). Assume a feature is modified, then it is possible to search for all features in other storages, maps, and collections that have `origin` set to this feature. Actually, we only search for features having the same originating feature, so who have an `origin` starting with the ID of the originating feature. When found, a rebase can be started.
         *
         * To perform an actual rebase, the client that will do the rebase, will first find the latest state of the originating feature, _ORIGIN-HEAD_. Then it will read the state that `origin` refers to as _ORIGIN-BASE_, calculating a difference between the _ORIGIN-HEAD_ and the _ORIGIN-BASE_, being _ORIGIN-DIFF_. Now the history of the rebase feature need to read, until the moment is found, where the origin was set. This is the _REBASE-BASE_ state. Then the latest version of the feature is looked up, and read as _REBASE-HEAD_. This allows to calculate the difference, being _REBASE-DIFF_.
         *
         * Now, we have two differences, the _ORIGIN-DIFF_, describing what was changed in the origin since the feature was forked, and _REBASE-DIFF_, which describes what was change in the to rebase collection, since the feature was forked. This allows to add both differences, resulting in a _REBASE-PATCH_ or a conflict, if both collections modified the same properties. Some of these conflicts can be automatically solved by one wins over the other, but this is out of scope of this description. Eventually, when successfully created the _REBASE_PATCH_, this patch can be applied to _REBASE-BASE_ to produce a _REBASE-NEW_ state, that has _REBASE-HEAD_ as precedence state ([prev_tn]). When this is applied, the `origin` need to be updated to the one to which the rebase was performed.
         *
         * This was a simplified description, but should allow to understand the basic concepts and meaning of this property.
         *
         * The `origin` is sticky, the value is kept until updated, the feature is forked again, or the ID is changed.
         */
        @JvmField
        @JsStatic
        val origin = def(PgColumn::class, "origin") { self ->
            self._i = 20
            self._type = PgType.STRING
            self._extra = "STORAGE PLAIN COLLATE \"C\""
        }

        /**
         * The stringified [Guid][naksha.model.Guid] of the feature into which this feature was integrated.
         *
         * This value is set in the following situations:
         * - **Join**: If multiple features are deleted, and then replaced with a single feature, then the features that are deleted will have `action` set to `DELETED` and the `target` will refer to the feature into which they are joined. The target feature will have the `action` set to `JOINED` to indicate, that there are deleted features, which target this one.
         */
        @JvmField
        @JsStatic
        val target = def(PgColumn::class, "target") { self ->
            self._i = 21
            self._type = PgType.STRING
            self._extra = "STORAGE PLAIN COLLATE \"C\""
        }

        /**
         * A customer feature-type, [type][naksha.model.objects.NakshaFeature.type] of the [feature][naksha.model.objects.NakshaFeature], _null_ if it matches the [default-type of the collection][naksha.model.objects.NakshaCollection.defaultType].
         */
        @JvmField
        @JsStatic
        val ft = def(PgColumn::class, "ft") { self ->
            self._i = 22
            self._type = PgType.STRING
            self._extra = "STORAGE PLAIN COLLATE \"C\""
        }

        /**
         * A custom string, _null_ if not used.
         */
        @JvmField
        @JsStatic
        val cs0 = def(PgColumn::class, "cs0") { self ->
            self._i = 23
            self._type = PgType.STRING
            self._extra = "STORAGE PLAIN COLLATE \"C\""
        }

        /**
         * A custom string, _null_ if not used.
         */
        @JvmField
        @JsStatic
        val cs1 = def(PgColumn::class, "cs1") { self ->
            self._i = 24
            self._type = PgType.STRING
            self._extra = "STORAGE PLAIN COLLATE \"C\""
        }

        /**
         * A custom string, _null_ if not used.
         */
        @JvmField
        @JsStatic
        val cs2 = def(PgColumn::class, "cs2") { self ->
            self._i = 25
            self._type = PgType.STRING
            self._extra = "STORAGE PLAIN COLLATE \"C\""
        }

        /**
         * A custom string, _null_ if not used.
         */
        @JvmField
        @JsStatic
        val cs3 = def(PgColumn::class, "cs3") { self ->
            self._i = 26
            self._type = PgType.STRING
            self._extra = "STORAGE PLAIN COLLATE \"C\""
        }

        // Assuming:
        // - id is not more than 60 byte
        // - app_id and author are not more than 30 byte
        // - origin and target are not more 60 byte
        // - some byte reserved for cs0, cs1, cs2, cs3 (60 byte total)
        //= 91 + 60 + 60 + 120 + 60 = 391 byte

        /**
         * The [tags][naksha.model.TagMap] of the [tuple][naksha.model.Tuple], stored as map.
         */
        @JvmField
        @JsStatic
        val tags = def(PgColumn::class, "tags") { self ->
            self._i = 27
            self._type = PgType.BYTE_ARRAY
            self._extra = "STORAGE EXTERNAL"
        }

        /**
         * The reference-point of the [feature][naksha.model.objects.NakshaFeature].
         */
        @JvmField
        @JsStatic
        val ref_point = def(PgColumn::class, "ref_point") { self ->
            self._i = 28
            self._type = PgType.BYTE_ARRAY
            self._extra = "STORAGE EXTERNAL"
        }

        /**
         * The geometry of the [feature][naksha.model.objects.NakshaFeature].
         */
        @JvmField
        @JsStatic
        val geo = def(PgColumn::class, "geo") { self ->
            self._i = 29
            self._type = PgType.BYTE_ARRAY
            self._extra = "STORAGE EXTERNAL"
        }

        // When we want to keep tags and geometry as well untoasted, we should use 1024 toast_tuple_target!

        /**
         * The serialized [feature][naksha.model.objects.NakshaFeature].
         */
        @JvmField
        @JsStatic
        val feature = def(PgColumn::class, "feature") { self ->
            self._i = 30
            self._type = PgType.BYTE_ARRAY
            self._extra = "STORAGE EXTERNAL"
        }

        /**
         * An arbitrary binary attachment.
         */
        @JvmField
        @JsStatic
        val attachment = def(PgColumn::class, "attachment") { self ->
            self._i = 31
            self._type = PgType.BYTE_ARRAY
            self._extra = "STORAGE EXTENDED"
        }

        /**
         * All columns being used with Naksha.
         */
        @JvmField
        @JsStatic
        val allColumns = listOf(
            updated_at, created_at, author_ts, txn, txn_next,
            cv0, cv1, cv2, cv3,
            uid, change_count, hash, here_tile, flags,
            tn, prev_tn, base_tn,
            id, app_id, author, origin, target, ft,
            cs0, cs1, cs2, cs3,
            tags, ref_point, geo, feature, attachment
        )

        init {
            // This is only self-check code.
            for ((i, col) in allColumns.withIndex()) {
                check(i == col.i) { "Invalid columns, column '${col.name}' should be at index ${col.i}, but found at $i" }
            }
        }

        /**
         * Returns the [PgColumn] that matches to the official [MetaColumn].
         * @param metaColumn the [MetaColumn] to resolve.
         * @return the [PgColumn] that matches this [MetaColumn]; if any.
         */
        @JvmStatic
        @JsStatic
        fun ofRowColumn(metaColumn: MetaColumn): PgColumn? = when (metaColumn.name) {
            MetaColumn.UPDATED_AT -> updated_at
            MetaColumn.CREATED_AT -> created_at
            MetaColumn.AUTHOR_TS -> author_ts
            MetaColumn.NEXT_VERSION -> txn_next
            MetaColumn.CV0 -> cv0
            MetaColumn.CV1 -> cv1
            MetaColumn.CV2 -> cv2
            MetaColumn.CV3 -> cv3
            MetaColumn.VERSION -> txn
            MetaColumn.PREV_TUPLE_NUMBER -> prev_tn
            MetaColumn.BASE_TUPLE_NUMBER -> base_tn
            MetaColumn.HASH -> hash
            MetaColumn.CHANGE_COUNT -> change_count
            MetaColumn.HERE_TILE -> here_tile
            MetaColumn.FLAGS -> flags
            MetaColumn.ID -> id
            MetaColumn.APP_ID -> app_id
            MetaColumn.AUTHOR -> author
            MetaColumn.ORIGIN -> origin
            MetaColumn.TARGET -> target
            MetaColumn.FEATURE_TYPE -> ft
            MetaColumn.CS0 -> cs0
            MetaColumn.CS1 -> cs1
            MetaColumn.CS2 -> cs2
            MetaColumn.CS3 -> cs3
            MetaColumn.TAGS -> tags
            MetaColumn.REF_POINT -> ref_point
            MetaColumn.GEOMETRY -> geo
            MetaColumn.FEATURE -> feature
            MetaColumn.ATTACHMENT -> attachment
            else -> null
        }
    }

    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = PgColumn::class
    override fun initClass() {}
}
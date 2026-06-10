@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.Int64
import naksha.base.JsEnum
import naksha.model.DataEncoding
import naksha.model.objects.Member
import naksha.model.objects.MemberType
import naksha.model.request.query.MetaColumn
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass
import naksha.model.TupleNumberVariant.TupleNumberVariant_C.B64
import naksha.model.TupleNumberVariant.TupleNumberVariant_C.B128

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
 * **Note**: We order the columns by intention, to minimize the storage size. The _bytea_ columns will be GZIP compressed on demand by the client storing the data (see [DataEncoding]). Note that in some cases this is not useful, for example, when a `TWKB` geometry or reference-point is given, it is often so small, that compression would increase the size. The general rule is, that anything being smaller than 100 byte, should not be compressed, which only leaves [feature] and [attachment] as compression candidates. We know that [feature] is stored GZIP compressed, therefore the only candidate left is [attachment]. So, we store [feature] _EXTERNAL_, [attachment] _EXTENDED_, and everything else as _PLAIN_ to keep it available for fast access. [tags] is stored as raw `jsonb` so the column itself is directly indexable (GIN); Postgres will TOAST/compress it automatically.
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
            @Suppress("UselessCallOnNotNull")
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
         * Prevents either compression or out-of-line storage. This is the only possible strategy for columns of non-TOAST-able data types.
         * @since 3.0
         */
        const val PLAIN = "PLAIN"

        /**
         * Allows both compression and out-of-line storage. This is the default for most TOAST-able data types. Compression will be attempted first, then out-of-line storage if the row is still too big.
         * @since 3.0
         */
        const val EXTENDED = "EXTENDED"

        /**
         * Allows out-of-line storage but not compression. Use of EXTERNAL will make substring operations on wide text and bytea columns faster (at the penalty of increased storage space) because these operations are optimized to fetch only the required parts of the out-of-line value when it is not compressed.
         * @since 3.0
         */
        const val EXTERNAL = "EXTERNAL"

        /**
         * Allows compression but not out-of-line storage. (Actually, out-of-line storage will still be performed for such columns, but only as a last resort when there is no other way to make the row small enough to fit on a page.)
         * @since 3.0
         */
        const val MAIN = "MAIN"

        /**
         * Returns the columns instance for the given name.
         * @param columnName the column name.
         * @return the column enumeration value.
         * @since 3.0
         */
        @JvmStatic
        @JsStatic
        fun of(columnName: String): PgColumn = get(columnName, PgColumn::class)

        /**
         * The epoch timestamp in millisecond when the [tuple][naksha.model.Tuple] was produced, which is the last time the feature was modified.
         *
         * **Optional** — `NULL` is allowed; NULL means the timestamp was not recorded.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val updated_at = def(PgColumn::class, "updated_at") { self ->
            self._i = 3
            self._type = PgType.INT64
        }

        /**
         * The epoch timestamp in millisecond when the [feature][naksha.model.objects.NakshaFeature] was originally created. If the value is _null_, this means this [tuple][naksha.model.Tuple] is the initial state of the [feature][naksha.model.objects.NakshaFeature], so the value is the same as [updated_at].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val created_at = def(PgColumn::class, "created_at") { self ->
            self._i = 4
            self._type = PgType.INT64
        }

        /**
         * The epoch timestamp in millisecond when the [feature][naksha.model.objects.NakshaFeature] was modified last by the author. If the value is _null_, this means this [tuple][naksha.model.Tuple] was changed by the author, so the value is the same as [updated_at].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val author_ts = def(PgColumn::class, "author_ts") { self ->
            self._i = 5
            self._type = PgType.INT64
        }

        /**
         * If this has a custom value, otherwise _null_.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val cv0 = def(PgColumn::class, "cv0") { self ->
            self._i = 7
            self._type = PgType.DOUBLE
        }

        /**
         * If this has a custom value, otherwise _null_.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val cv1 = def(PgColumn::class, "cv1") { self ->
            self._i = 8
            self._type = PgType.DOUBLE
        }

        /**
         * If this has a custom value, otherwise _null_.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val cv2 = def(PgColumn::class, "cv2") { self ->
            self._i = 9
            self._type = PgType.DOUBLE
        }

        /**
         * If this has a custom value, otherwise _null_.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val cv3 = def(PgColumn::class, "cv3") { self ->
            self._i = 10
            self._type = PgType.DOUBLE
        }

        /**
         * The unique hash of this [tuple][naksha.model.Tuple] (state), calculated by the storage using the static [Metadata.calculateHash] method.
         *
         * **Optional** — `NULL` is allowed.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val hash = def(PgColumn::class, "hash") { self ->
            self._i = 11
            self._type = PgType.INT
        }

        /**
         * The binary [HERE tile-key][naksha.geo.HereTile.intKey] of the reference-point of the [tuple][naksha.model.Tuple] (state). This is calculated using the static [Metadata.calculateHereTile] method.
         *
         * **Optional** — `NULL` is allowed; NULL means no reference point / tile is known.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val here_tile = def(PgColumn::class, "here_tile") { self ->
            self._i = 12
            self._type = PgType.INT
        }

        /**
         * The `change-count`.
         *
         * **Optional** — `NULL` is allowed.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val cc = def(PgColumn::class, "cc") { self ->
            self._i = 13
            self._type = PgType.INT
        }

        /**
         * The feature-number — the per-collection identifier of the feature this tuple belongs to.
         *
         * Together with [version], forms the primary identification of a tuple within a collection. The lower 16 bits of this value are used as the partition key for distribution partitioning (see [naksha.model.Naksha.featureNumber]).
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val fn = def(PgColumn::class, "fn") { self ->
            self._i = 0
            self._type = PgType.INT64
            self._extra = "STORAGE $PLAIN NOT NULL"
        }

        /**
         * The version (with action in the lower 2 bits) of this tuple.
         *
         * Together with [fn], forms the primary identification of a tuple within a collection. See [naksha.model.Version] for the layout.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val version = def(PgColumn::class, "version") { self ->
            self._i = 1
            self._type = PgType.INT64
            self._extra = "STORAGE $PLAIN NOT NULL"
        }

        /**
         * The next version of the tuple (with action in the lower 2 bits).
         *
         * Stored only in _HISTORY_ tables. In _HEAD_ this is intrinsically [HEAD][naksha.model.Version.HEAD] (the row is the head state); the column is omitted from HEAD DDL.
         *
         * For a tombstone state (final row of a feature's lifetime), this equals the row's own `version`.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val next_version = def(PgColumn::class, "next_version") { self ->
            self._i = 2
            self._type = PgType.INT64
            self._extra = "STORAGE $PLAIN" // prevents out-of-line storage
        }

        /**
         * The [tuple-number][naksha.model.TupleNumber] of the _BASE_ state upon which a [three-way-merge](https://en.wikipedia.org/wiki/Merge_(version_control)#Three-way_merge) was done, using the [128-bit encoding][B128].
         *
         * This value is set, when a client reads the _HEAD_ state of a feature, then modifies it into some _NEW_ state, and tries to save its changes, but meanwhile other clients did the same, and a conflict arises. The changes the client did are based upon an older _HEAD_, which we will name _BASE_, and do not reflect the changes the other clients did meanwhile.
         *
         * In this situation an automatic [three-way-merge](https://en.wikipedia.org/wiki/Merge_(version_control)#Three-way_merge) can be done. The changes of the other clients are calculated as difference between _HEAD_ and _BASE_, and the changes the client did is calculated as difference between _NEW_ and _BASE_. Then the difference are added to a patch. This can fail, if both clients changes the same properties, what will cause a conflict. If successful, the patch is applied to _BASE_ and will produce a new _NEW2_ state, that actually contains the changes of the client plus the changes of the other clients. This _NEW2_ state can be written into the storage; it references the old _HEAD_ as its previous tuple. This can fail again, if others were faster in doing the same, but is an idempotent operation, and can simply be repeated until either conflicting or solved.
         *
         * To document that this happened, the shared _BASE_ state is referred vai `base_tn`.
         *
         * This technically allows to calculate back, what the client actually modified. For this, the difference between _HEAD_ and _BASE_ is calculated, and then the difference between the previous tuple and _BASE_ is subtracted, resulting in a patch that can be applied to _BASE_ to receive the original _NEW_ state the client had in memory and wanted to persist. This difference will as well document which properties were changed by the client.
         *
         * The encoding stores, in order, Big-Endian encoded:
         * - feature-number: 64
         * - version (with action in lower 2 bits): 64
         * @since 3.0
         * @see [B128]
         */
        @JvmField
        @JsStatic
        val base_tn = def(PgColumn::class, "base_tn") { self ->
            self._i = 24
            self._type = PgType.BYTE_ARRAY
            self._extra = "STORAGE $PLAIN" // prevents either compression or out-of-line storage
        }

        /**
         * The feature-id.
         *
         * **Conditionally mandatory**:
         * - When `fn < 0` (named feature, MSB set): `id` MUST NOT be `NULL` — it carries the user-supplied string identifier.
         * - When `fn >= 0` (anonymous / numeric feature): `id` MUST be `NULL` — the logical id is `fn` decimally encoded.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val id = def(PgColumn::class, "id") { self ->
            self._i = 14
            self._type = PgType.STRING
            self._extra = "STORAGE $PLAIN COLLATE \"C\"" // prevents either compression or out-of-line storage; NOT NULL enforced via CHECK constraint
        }

        /**
         * The application-id of the application that produced a [tuple][naksha.model.Tuple].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val app_id = def(PgColumn::class, "app_id") { self ->
            self._i = 15
            self._type = PgType.STRING
            self._extra = "STORAGE $PLAIN COLLATE \"C\""
        }

        /**
         * The author that takes ownership for the [tuple][naksha.model.Tuple].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val author = def(PgColumn::class, "author") { self ->
            self._i = 16
            self._type = PgType.STRING
            self._extra = "STORAGE $PLAIN COLLATE \"C\""
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
         * Now, we have two differences, the _ORIGIN-DIFF_, describing what was changed in the origin since the feature was forked, and _REBASE-DIFF_, which describes what was change in the to rebase collection, since the feature was forked. This allows to add both differences, resulting in a _REBASE-PATCH_ or a conflict, if both collections modified the same properties. Some of these conflicts can be automatically solved by one wins over the other, but this is out of scope of this description. Eventually, when successfully created the _REBASE_PATCH_, this patch can be applied to _REBASE-BASE_ to produce a _REBASE-NEW_ state, that has _REBASE-HEAD_ as precedence state. When this is applied, the `origin` need to be updated to the one to which the rebase was performed.
         *
         * This was a simplified description, but should allow to understand the basic concepts and meaning of this property.
         *
         * The `origin` is sticky, the value is kept until updated, the feature is forked again, or the ID is changed.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val origin = def(PgColumn::class, "origin") { self ->
            self._i = 17
            self._type = PgType.STRING
            self._extra = "STORAGE $PLAIN COLLATE \"C\"" // prevents either compression or out-of-line storage
        }

        /**
         * The stringified [Guid][naksha.model.Guid] of the feature into which this feature was integrated.
         *
         * This value is set in the following situations:
         * - **Join**: If multiple features are deleted, and then replaced with a single feature, then the features that are deleted will have `action` set to `DELETED` and the `target` will refer to the feature into which they are joined. The target feature will have the `action` set to `JOINED` to indicate, that there are deleted features, which target this one.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val target = def(PgColumn::class, "target") { self ->
            self._i = 18
            self._type = PgType.STRING
            self._extra = "STORAGE $PLAIN COLLATE \"C\"" // prevents either compression or out-of-line storage
        }

        /**
         * A customer feature-type, [type][naksha.model.objects.NakshaFeature.type] of the [feature][naksha.model.objects.NakshaFeature], _null_ if it matches the [default-type of the collection][naksha.model.objects.NakshaCollection.typeDefaultValue].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val ft = def(PgColumn::class, "ft") { self ->
            self._i = 19
            self._type = PgType.STRING
            self._extra = "STORAGE $PLAIN COLLATE \"C\"" // prevents either compression or out-of-line storage
        }

        /**
         * A custom string, _null_ if not used.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val cs0 = def(PgColumn::class, "cs0") { self ->
            self._i = 20
            self._type = PgType.STRING
            self._extra = "STORAGE $PLAIN COLLATE \"C\"" // prevents either compression or out-of-line storage
        }

        /**
         * A custom string, _null_ if not used.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val cs1 = def(PgColumn::class, "cs1") { self ->
            self._i = 21
            self._type = PgType.STRING
            self._extra = "STORAGE $PLAIN COLLATE \"C\"" // prevents either compression or out-of-line storage
        }

        /**
         * A custom string, _null_ if not used.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val cs2 = def(PgColumn::class, "cs2") { self ->
            self._i = 22
            self._type = PgType.STRING
            self._extra = "STORAGE $PLAIN COLLATE \"C\"" // prevents either compression or out-of-line storage
        }

        /**
         * A custom string, _null_ if not used.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val cs3 = def(PgColumn::class, "cs3") { self ->
            self._i = 23
            self._type = PgType.STRING
            self._extra = "STORAGE $PLAIN COLLATE \"C\"" // prevents either compression or out-of-line storage
        }

        /**
         * The tags of the [tuple][naksha.model.Tuple], stored as raw `jsonb`.
         *
         * By default ([naksha.model.objects.MemberType.SET]) this is a JSON array of unique strings
         * ([naksha.model.TagList]), persisted unmodified so the element order is preserved. When the
         * tags member is declared as [naksha.model.objects.MemberType.TAGS] or
         * [naksha.model.objects.MemberType.TAGS_FROM_ARRAY], it is a JSON object
         * ([naksha.model.TagMap]) instead.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val tags = def(PgColumn::class, "tags") { self ->
            self._i = 25
            self._type = PgType.JSONB
        }

        /**
         * The reference-point of the [feature][naksha.model.objects.NakshaFeature].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val ref_point = def(PgColumn::class, "ref_point") { self ->
            self._i = 26
            self._type = PgType.BYTE_ARRAY
            self._extra = "STORAGE $EXTERNAL"
        }

        /**
         * The geometry of the [feature][naksha.model.objects.NakshaFeature].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val geo = def(PgColumn::class, "geo") { self ->
            self._i = 27
            self._type = PgType.BYTE_ARRAY
            self._extra = "STORAGE $EXTERNAL"
        }

        /**
         * The serialized [feature][naksha.model.objects.NakshaFeature].
         *
         * **Mandatory** — MUST NOT be `NULL`. Every tuple must carry a serialized feature blob.
         * Not indexable; encoding is configurable (default: JBON2_GZIP).
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val feature = def(PgColumn::class, "feature") { self ->
            self._i = 28
            self._type = PgType.BYTE_ARRAY
            self._extra = "STORAGE $EXTERNAL NOT NULL"
        }

        /**
         * An arbitrary binary attachment.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val attachment = def(PgColumn::class, "attachment") { self ->
            self._i = 29
            self._type = PgType.BYTE_ARRAY
            self._extra = "STORAGE $EXTERNAL"
        }

        /**
         * The global-book-number that references the JBON2 global dictionary entry needed to decode
         * the [feature] blob when global-book encoding is used. Assigned by the sequencer.
         *
         * **Optional** — `NULL` when no global book is in use for this tuple.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val gbn = def(PgColumn::class, "gbn") { self ->
            self._i = 6
            self._type = PgType.INT64
            self._extra = "STORAGE $PLAIN"
        }

        /**
         * Publisher-assigned gap-free sequential number ordered by **visibility** (not execution order).
         * After publish-number `N` comes `N+1` without holes, regardless of transaction commit order.
         * Assigned by an external publisher component. `NULL` until published.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val pn = def(PgColumn::class, "pn") { self ->
            self._i = 30
            self._type = PgType.INT64
            self._extra = "STORAGE $PLAIN"
        }

        /**
         * Millisecond epoch timestamp at which the publisher recognised and sequenced this transaction,
         * assigning it its [pn]. `NULL` until published.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val pt = def(PgColumn::class, "pt") { self ->
            self._i = 31
            self._type = PgType.INT64
            self._extra = "STORAGE $PLAIN"
        }

        /**
         * HERE-internal global version number assigned by HERE's global sequencing infrastructure.
         * `NULL` until the global sequencer has processed the transaction.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val gv = def(PgColumn::class, "gv") { self ->
            self._i = 32
            self._type = PgType.INT64
            self._extra = "STORAGE $PLAIN"
        }

        /**
         * All columns being used with Naksha, ordered by type alignment group for minimal PostgreSQL
         * tuple padding:
         * 1. INT64 (8-byte): fn, version, next_version, updated_at, created_at, author_ts, gbn
         * 2. FLOAT64 (8-byte): cv0, cv1, cv2, cv3
         * 3. INT32 (4-byte): hash, here_tile, cc
         * 4. STRING (var): id, app_id, author, origin, target, ft, cs0, cs1, cs2, cs3
         * 5. BYTE_ARRAY (var): base_tn, tags, ref_point, geo, feature, attachment
         *
         * The SPECIAL columns ([pn], [pt], [gv]) follow the same INT64 slot rule but are
         * only physically present when explicitly declared; they live in [allColumnsByName] only.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val allColumns = listOf(
            // INT64 group
            fn, version, next_version,
            updated_at, created_at, author_ts,
            gbn,
            // FLOAT64 group
            cv0, cv1, cv2, cv3,
            // INT32 group
            hash, here_tile, cc,
            // STRING group
            id, app_id, author, origin, target, ft,
            cs0, cs1, cs2, cs3,
            // BYTE_ARRAY group
            base_tn, tags, ref_point, geo, feature, attachment,
        )

        /**
         * Columns present in a _HEAD_ table — same as [allColumns] except for [next_version], which is intrinsically [HEAD][naksha.model.Version.HEAD] in HEAD rows and therefore not stored.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val headColumns = allColumns.filter { it !== next_version }

        /**
         * All columns indexed by name, for fast lookup.
         * Includes [allColumns] plus the optional SPECIAL columns ([pn], [pt], [gv]) that are
         * only physically present when a collection explicitly declares them as members.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val allColumnsByName: Map<String, PgColumn> = (allColumns + listOf(pn, pt, gv)).associateBy { it.name }

        /**
         * The minimal mandatory columns for a HEAD / META / DELETED table:
         * `fn`, `version`, `id`, `feature`, and `gbn`.
         *
         * Used when [naksha.model.objects.NakshaCollection.members] is an **empty list** (explicitly set to `[]`)
         * to create a lean table with no optional built-in columns.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val mandatoryColumns: List<PgColumn> = listOf(fn, version, id, feature, gbn)

        /**
         * The minimal mandatory columns for a HISTORY table:
         * [mandatoryColumns] plus [next_version] (which is the HISTORY partition key).
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val mandatoryHistoryColumns: List<PgColumn> = listOf(fn, version, next_version, id, feature, gbn)

        /**
         * The names of all HEAD database columns, as comma-separated list.
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val headColumnNames = headColumns.joinToString(",") { it.name }

        /**
         * The names of all database columns, as comma separated list.
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val allColumnNames = allColumns.joinToString(",") { it.name }

        /**
         * The placeholders when inserting all columns, like &#36;1, &#36;2, ...
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val allColumnPlaceholders = allColumns.joinToString(",") { "\$${(it.i + 1)}" }

        /**
         * The type names for the values of all columns, for example `int8` for a column being [Int64].
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val allColumnTypeNames = Array(allColumns.size) { allColumns[it].type.text }

        /**
         * The array type names for the values of all columns, for example `int8[]` for a column being [Int64].
         *
         * To be used when multiple rows need to be transferred into the database, for example, creating a plan for all columns:
         * ```kotlin
         * val SQL = """WITH input AS (
         *   SELECT * FROM
         *   UNNEST(${allColumnPlaceholders}) AS t($allColumnNames)
         * ), ..."""
         * val plan = conn.prepare(SQL, allColumnArrayTypeNames)
         * val values = Array<Any?>(n) { ... } // an array of arrays
         * val cursor = plan.execute(values)
         * ```
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val allColumnArrayTypeNames = Array(allColumns.size) { allColumns[it].type.text+"[]" }

        /**
         * All columns that are needed when we copy a feature from _HEAD_ into _HISTORY_, so all except for:
         * - [next_version]
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val copyIntoHistoryColumns = listOf(
            updated_at, created_at, author_ts,
            cv0, cv1, cv2, cv3,
            hash, here_tile, cc,
            fn, version, base_tn, // removed: next_version, prev_tn
            id, app_id, author, origin, target, ft,
            cs0, cs1, cs2, cs3,
            tags, ref_point, geo, feature, attachment,
            gbn,
            pn, pt, gv,
        )

        /**
         * The names of all database columns, as comma separated list.
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val copyIntoHistoryColumnNames = copyIntoHistoryColumns.joinToString(",") { it.name }

        /**
         * All columns that we copy from the user data, when we update a row.
         *
         * This excludes the columns that need updates:
         * - [cc] - we need to increment change-count
         * @since 3.0
         */
        @JvmField
        @JsStatic
         val updateColumns = listOf(
            updated_at, created_at, author_ts,
            cv0, cv1, cv2, cv3,
            hash, here_tile, // removed: cc
            base_tn, // removed: fn, version (PK columns; not set via updates), next_version (HEAD has none)
            id, app_id, author, origin, target, ft,
            cs0, cs1, cs2, cs3,
            tags, ref_point, geo, feature, // removed: attachment (needs special handling)
            gbn,
        )

        /**
         * The names of all database columns, as comma separated list.
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val updateColumnsNames = updateColumns.joinToString(",") { it.name }

        /**
         * All columns that we copy, when we create a tombstone state (deleted).
         *
         * In that case we copy from _HEAD_ into a temporary CTE table, then further to _HISTORY_ and/or _SHADOW_, but we need to update some columns, therefore this excludes the columns that need updates:
         * - [next_tn] - will become the current [tn] to signal tombstone state _(dead-end)_
         * - [tn] - must be updated to match current `version`, with action bits set to DELETED (this is the `final_tn`)
         * - [fn] - copied from the HEAD row directly
         * - [base_tn] - needs to be set to `null`
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val tombstoneColumns = listOf(
            updated_at, created_at, author_ts,
            cv0, cv1, cv2, cv3,
            hash, here_tile, // removed: cc
            // removed: fn, version, next_version, and base_tn,
            id, app_id, author, origin, target, ft,
            cs0, cs1, cs2, cs3,
            tags, ref_point, geo, feature, attachment,
            gbn,
        )

        init {
            // This is only self-check code.
            for ((i, col) in allColumns.withIndex()) {
                check(i == col.i) { "Invalid columns, column '${col.name}' should be at index ${col.i}, but found at $i" }
            }
        }

        /**
         * Maps a [PgColumn] to its equivalent [MemberType], used when generating [Member] objects
         * for mandatory and default member lists.
         */
        internal fun pgTypeToMemberType(col: PgColumn): MemberType = when {
            col === geo || col === ref_point -> MemberType.SPATIAL
            else -> when (col.type) {
                PgType.INT64   -> MemberType.INT64
                PgType.DOUBLE  -> MemberType.FLOAT64
                PgType.INT     -> MemberType.INT32
                PgType.SHORT   -> MemberType.INT16
                PgType.FLOAT   -> MemberType.FLOAT32
                PgType.BOOLEAN -> MemberType.BOOLEAN
                PgType.STRING  -> MemberType.STRING
                PgType.BYTE_ARRAY -> MemberType.BYTE_ARRAY
                else           -> MemberType.STRING
            }
        }

        /**
         * The mandatory [Member]s that the storage always injects into every collection, regardless of
         * what the client provides: `fn` (INT64), `version` (INT64), `id` (STRING), `feature` (BYTE_ARRAY).
         *
         * These are derived from [mandatoryColumns]. Clients must not declare them with a different type.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val mandatoryMembers: List<Member> = mandatoryColumns.map { col ->
            Member(col.name, pgTypeToMemberType(col))
        }

        /**
         * The mandatory [Member]s for a HISTORY table: [mandatoryMembers] plus `next_version` (INT64).
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val mandatoryHistoryMembers: List<Member> = mandatoryHistoryColumns.map { col ->
            Member(col.name, pgTypeToMemberType(col))
        }

        /**
         * The default [Member]s injected when the client does **not** provide a [members][naksha.model.objects.NakshaCollection.members]
         * list (backward-compatible full schema). These correspond to all optional built-in columns
         * (i.e. [headColumns] minus [mandatoryColumns]).
         * @since 3.0
         */
        @JsStatic
        val defaultMembers: List<Member> by lazy {
            val mandatory = mandatoryColumns.map { it.name }.toSet()
            headColumns.filter { it.name !in mandatory }.map { col ->
                Member(col.name, pgTypeToMemberType(col))
            }
        }

        /**
         * Returns the [PgColumn] that matches to the official [MetaColumn].
         * @param metaColumn the [MetaColumn] to resolve.
         * @return the [PgColumn] that matches this [MetaColumn]; if any.
         * @since 3.0
         */
        @JvmStatic
        @JsStatic
        fun ofRowColumn(metaColumn: MetaColumn): PgColumn? = when (metaColumn.name) {
            MetaColumn.NEXT_VERSION -> next_version
            MetaColumn.UPDATED_AT -> updated_at
            MetaColumn.CREATED_AT -> created_at
            MetaColumn.AUTHOR_TS -> author_ts
            MetaColumn.CV0 -> cv0
            MetaColumn.CV1 -> cv1
            MetaColumn.CV2 -> cv2
            MetaColumn.CV3 -> cv3
            MetaColumn.HASH -> hash
            MetaColumn.HERE_TILE -> here_tile
            MetaColumn.CHANGE_COUNT -> cc
            MetaColumn.TUPLE_NUMBER -> fn // tn is split: callers comparing the binary form must decode to (fn, version)
            MetaColumn.VERSION -> version
            MetaColumn.BASE_TUPLE_NUMBER -> base_tn
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
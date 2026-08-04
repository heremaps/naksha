@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Action
import naksha.base.PAnyArray
import naksha.base.PAnyMap
import naksha.base.Id
import naksha.base.Id.Id_C.ADMIN_CATALOG_ID
import naksha.base.Id.Id_C.BOOKS_COL_ID
import naksha.base.Id.Id_C.CATALOGS_COL_ID
import naksha.base.Id.Id_C.COLLECTIONS_COL_ID
import naksha.base.PTypedArray
import naksha.base.PTypedMap
import naksha.base.Base.BaseCompanion.fromJSON
import naksha.base.Base.BaseCompanion.gzipDeflate
import naksha.base.Base.BaseCompanion.gzipInflate
import naksha.base.PlatformList
import naksha.base.PlatformMap
import naksha.base.PlatformMapApi.PlatformMapApiCompanion.map_get
import naksha.base.WeakRef
import naksha.jbon.IBook
import naksha.model.objects.Member
import naksha.geo.SpGeometry
import naksha.geo.SpType
import naksha.jbon.BookType
import naksha.jbon.HeapBook
import naksha.jbon.JB2_MAGIC
import naksha.jbon.JbDecoder2
import naksha.jbon.JbEncoder2
import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.base.NakshaException
import naksha.base.Base.BaseCompanion.FAL
import naksha.base.Base.BaseCompanion.UNDEFINED
import naksha.base.TupleNumber
import naksha.base.Version
import naksha.base.collectionNotFound
import naksha.base.illegalArg
import naksha.base.illegalState
import naksha.base.internalError
import naksha.base.mapNotFound
import naksha.model.objects.MemberType
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaDatabase
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StandardMembers
import naksha.model.objects.StandardMembers.StandardMembers_C.ChangeCountMember
import naksha.model.objects.StandardMembers.StandardMembers_C.GlobalBookFeatureNumber
import naksha.model.objects.StandardMembers.StandardMembers_C.IdMember
import naksha.model.objects.StandardMembers.StandardMembers_C.NextVersionMember
import naksha.model.objects.StandardMembers.StandardMembers_C.OriginMember
import naksha.model.objects.StandardMembers.StandardMembers_C.TnMember
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * A tuple represents a specific immutable state of a feature in binary encoding.
 * @since 3.0
 */
@JsExport
data class Tuple @JvmOverloads constructor(
    /**
     * Feature serialized with the encoding described by the collection's dataEncoding.
     * @since 3.0
     */
    @JvmField val featureBytes: ByteArray,

    /**
     * The members book provided by storage at read time. Contains dedicated member values, such as `id`, `tn`, etc.
     * @since 3.0
     */
    @JvmField val membersBook: IBook,

    /**
     * After encoding a [NakshaFeature] into a [Tuple] using [encodeObject] method, the [previousTupleNumber] will be set by the encoder to the [naksha.base.TupleNumber] of the given feature; if it had any.
     *
     * This is metadata, it can as well be set manually, when a tuple is read from a storage.
     * @since 3.0
     */
    @JvmField var previousTupleNumber: TupleNumber? = null
) {

    companion object Tuple_C {
       /**
         * Encodes the given [NakshaCollection] into `JBON2` bytes and members book.
         *
         * ### Note
         * Does not modify the given object.
         *
         * @param obj the collection to encode.
         * @param session the session for which to encode the object; declares the version.
         * @param requestedAction an action the client wants to perform; if `null`, auto-decided.
         * @param atomic a hint if the operation **must** be atomic; if `null`, auto-decided.
         * @return the encoded feature bytes (`JBON2`, optionally GZIP-compressed, auto-decided).
         * @since 3.0
         * @throws NakshaException if any error happens when encoding.
         */
        @JsStatic
        @JvmStatic
        @JvmOverloads
        fun encodeCollection(
            obj: NakshaCollection,
            session: IWriteSession,
            requestedAction: Action? = null,
            atomic: Boolean? = null
        ): Tuple {
            val catalog = session.getCatalogByNumber(obj.catalogId.intValue)
                ?: throw mapNotFound("${FAL}The catalog '${obj.catalogId}' does not exist")
            val collection = session.getCollectionByNumber(catalog, COLLECTIONS_COL_ID.intValue)
                ?: throw collectionNotFound("${FAL}The collection '${COLLECTIONS_COL_ID}' does not exist")
            return encodeObject(obj, session, collection, null, requestedAction, atomic)
        }

        /**
         * Encodes the given [NakshaCatalog] into `JBON2` bytes and members book.
         *
         * ### Note
         * Does not modify the given object.
         *
         * @param obj the catalog to encode.
         * @param session the session for which to encode the object; declares the version.
         * @param requestedAction an action the client wants to perform; if `null`, auto-decided.
         * @param atomic a hint if the operation **must** be atomic; if `null`, auto-decided.
         * @return the encoded feature bytes (`JBON2`, optionally GZIP-compressed, auto-decided).
         * @since 3.0
         * @throws NakshaException if any error happens when encoding.
         */
        @JsStatic
        @JvmStatic
        @JvmOverloads
        fun encodeCatalog(
            obj: NakshaCatalog,
            session: IWriteSession,
            requestedAction: Action? = null,
            atomic: Boolean? = null
        ): Tuple {
            val catalog = session.getCatalogByNumber(ADMIN_CATALOG_ID.intValue)
                ?: throw mapNotFound("${FAL}The catalog '$ADMIN_CATALOG_ID' does not exist")
            val collection = session.getCollectionByNumber(catalog, CATALOGS_COL_ID.intValue)
                ?: throw collectionNotFound("${FAL}The collection '$CATALOGS_COL_ID' does not exist")
            return encodeObject(obj, session, collection, null, requestedAction, atomic)
        }

        /**
         * Encodes the given [object][PAnyMap] into `JBON2` bytes and members book.
         *
         * ### Note
         * Does not modify the given object.
         *
         * @param obj the object to encode.
         * @param collection the collection for which to encode the object; declares the members.
         * @param session the session for which to encode the object; declares the version.
         * @param globalBook the global book to use for encoding; if any.
         * @param forceAction an action the client wants to perform; if `null`, auto-decided.
         * @param atomic a hint if the operation **must** be atomic; if `null`, auto-decided.
         * @return the encoded feature bytes (`JBON2`, optionally GZIP-compressed, auto-decided).
         * @since 3.0
         * @throws NakshaException if any error happens when encoding.
         */
        @JsStatic
        @JvmStatic
        @JvmOverloads
        fun encodeObject(
            obj: PAnyMap,
            session: IWriteSession,
            collection: NakshaCollection,
            globalBook: IBook? = null,
            forceAction: Action? = null,
            atomic: Boolean? = null,
            upsert: Boolean = false
        ): Tuple {
            val idMember = collection.getMember(IdMember)
                ?: throw illegalArg("${FAL}The collection ${collection.id} does not have an 'id' member")
            val id = Id.fromValue(idMember.read(obj))
                ?: throw illegalArg("${FAL}The given objects has no value for member 'id'")

            val memberList = collection.useMembers()
            val processors = session.processors

            val databaseNumber: Long = collection.databaseId.number
            val catalogNumber: Int = collection.catalogId.intValue
            val collectionNumber: Int = collection.id.intValue
            val featureNumber: Long = id.number

            // Find members.
            val tnMember = collection.useMember(TnMember)
            val nextVersionMember = collection.useMember(NextVersionMember)
            val globalBookFnMember = collection.useMember(GlobalBookFeatureNumber)
            val originMember = collection.getMember(OriginMember)

            // Read members.
            val rawTn = tnMember.read(obj)
            var prevTn: TupleNumber? = TupleNumber.toTupleNumber(rawTn)
            var origin: String? = originMember?.readString(obj)

            // Detect CREATE, UPDATE , fork, and update.
            val action: Action
            if (prevTn != null) {
                if (databaseNumber != prevTn.databaseNumber ||
                    catalogNumber != prevTn.catalogNumber ||
                    collectionNumber != prevTn.collectionNumber ||
                    featureNumber != prevTn.featureNumber)
                {
                    // FORK: Client copied a feature from another collection or changed the `id`.
                    origin = prevTn.toString()
                    prevTn = null
                    action = when (forceAction) {
                        Action.CREATE -> Action.CREATE
                        Action.UPDATE -> throw illegalArg("Requested action UPDATE, but client provided a foreign state as 'uuid'")
                        Action.DELETE -> Action.DELETE
                        else -> Action.CREATE
                    }
                } else { // Client modified an existing state, prevTn is valid.
                    action = when (forceAction) {
                        // This is special:
                        // Normally, we should throw an exception, if prevTn.action is not DELETE, because
                        // creating an object and expecting the object to exist in a specific state is contradictory.
                        // However, by definition we need to support UPSERT, and for this operation we need to
                        // ignore the previous state in CREATE, because by definition the previous state only counts
                        // for the UPDATE part of the UPSERT.
                        Action.CREATE -> {
                            if (!upsert && prevTn.action != Action.DELETE) {
                                throw illegalArg("Requested ${if (atomic != true) "" else "atomic "} CREATE, but client provided the expected state as 'uuid'")
                            }
                            Action.CREATE
                        }
                        Action.UPDATE -> Action.UPDATE
                        Action.DELETE -> Action.DELETE
                        else -> Action.UPDATE
                    }
                }
            } else {
                // If the `uuid` was a simple string, no tuple-number, this is a fork from a foreign storage.
                if (rawTn is String) origin = rawTn
                action = when (forceAction) {
                    Action.CREATE-> Action.CREATE
                    Action.UPDATE -> if (atomic != true) Action.UPDATE else throw illegalArg("Requested atomic action UPDATE, but client did not provide a 'uuid' (expected state)")
                    Action.DELETE -> Action.DELETE
                    else -> Action.CREATE // When not decided, we assume CREATE
                }
            }

            // The transaction version carries the VERSION sentinel (=3) in its low two action bits;
            // each object records its OWN action (CREATE/UPDATE/DELETE) in the low two bit of its version.
            val version = (session.useTransaction().asVersion.number and -4L) or action.intValue.toLong()
            val newTn = TupleNumber(databaseNumber, catalogNumber, collectionNumber, featureNumber, version)

            val globalBookTn: TupleNumber?
            if (globalBook != null) {
                val gbDatabaseNumber = globalBook.databaseNumber
                    ?: throw illegalArg("${FAL}Invalid global book provided, missing database-number in book")
                val gbFeatureNumber = globalBook.featureNumber
                    ?: throw illegalArg("${FAL}Invalid global book provided, missing feature-number in book")
                if (newTn.databaseNumber != gbDatabaseNumber) {
                    throw illegalArg("The given global book is not located in the same storage as the feature")
                }
                val gbCatalogNumber = ADMIN_CATALOG_ID.intValue
                val gbCollectionNumber = BOOKS_COL_ID.intValue
                globalBookTn = TupleNumber(gbDatabaseNumber, gbCatalogNumber, gbCollectionNumber, gbFeatureNumber, 0L)
            } else {
                globalBookTn = null
            }

            // TODO: We must not modify the given feature, we do it for the sake of downward compatibility here.
            //       We need to fix this!
            //       To do this, we need to change the JbEncoder2, currently it does not support value overlay!
            originMember?.write(obj, origin)
            tnMember.write(obj, newTn)
            nextVersionMember.write(obj, UNDEFINED)
            globalBookFnMember.write(obj, globalBookTn?.featureNumber ?: UNDEFINED)

            // Create the members book with member in the order as defined in the NakshaCollection.
            // This is important, because references are positional indices into the members book,
            // and the reader (PgRows.getTuple) restores the tuple in the same order.
            val membersBook = HeapBook(BookType.MEMBER_BOOK)
            for (i in 0 until memberList.size) {
                val member = memberList[i] ?: throw illegalState("${FAL}Member must not be null")
                val memberName = member.id
                if (member.isVirtual()) continue
                var memberValue: Any? = when (memberName) {
                    // The `id` is null, when it is just the stringified feature-number.
                    IdMember.id -> if (id.isNumeric) null else id.text
                    TnMember.id -> newTn // this contains feature-number
                    NextVersionMember.id -> null
                    OriginMember.id -> origin
                    GlobalBookFeatureNumber.id -> globalBookTn?.featureNumber
                    ChangeCountMember.id -> (member.readInt64(obj) ?: 0L) + 1L
                    else -> member.read(obj)
                }
                val procs = processors[memberName]
                if (procs != null) {
                    for (proc in procs) {
                        memberValue = proc.processMember(session, collection, obj, member, memberValue)
                    }
                }
                memberValue = FeatureMemberValues.coerce(memberValue, member.dataType, id, memberName)
                membersBook.put(memberName, memberValue)
            }

            // Create the encoder with a custom member encoder.
            val encoder = JbEncoder2(globalBook)
            encoder.withMemberEncoder { path: Array<Any?>, pathEnd: Int, value: Any? ->
                // Find the member whose declared path matches the current position and redirect its value into the members-book.
                var index = -1
                members@ for (i in 0 until memberList.size) {
                    val member = memberList[i] ?: continue
                    if (member.isVirtual()) continue
                    val memberName = member.id
                    val memberPath = member.path
                    if (memberPath.size != pathEnd) continue
                    for (pi in 0 until pathEnd) {
                        if (path[pi] != memberPath[pi]) continue@members // test next member
                    }
                    // Path matches.
                    val memberIndex = membersBook.indexOfName(memberName)
                    if (memberIndex < 0) throw internalError("Member index should not be negative at this point")
                    index = memberIndex
                    break
                }
                index
            }

            // Encode the feature.
            val raw = encoder.buildTupleFromMap(obj)

            // Optionally GZIP.
            if (raw.size >= 1000) {
                val compressed = gzipDeflate(raw)
                if (compressed.size < raw.size) {
                    return Tuple(compressed, membersBook, prevTn)
                }
            }
            return Tuple(raw, membersBook, prevTn)
        }

        private fun isGzipped(bytes: ByteArray): Boolean =
            bytes.size >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()

        private fun isJbon2(bytes: ByteArray): Boolean =
            bytes.size >= 4 &&
                    bytes[0] == JB2_MAGIC[0] && bytes[1] == JB2_MAGIC[1] &&
                    bytes[2] == JB2_MAGIC[2] && bytes[3] == JB2_MAGIC[3]

        private fun isJson(bytes: ByteArray): Boolean {
            val b = bytes[0]
            return b == 0x7B.toByte() || b == 0x5B.toByte() ||
                    b == 0x20.toByte() || b == 0x09.toByte() ||
                    b == 0x0A.toByte() || b == 0x0D.toByte()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is Tuple && this.tupleNumber == other.tupleNumber
    }

    override fun hashCode(): Int = tupleNumber.hashCode()

    private var _weakRef: WeakRef<Tuple>? = null

    /**
     * A lazy created weak-reference to this tuple _(created on read)_.
     * @since 3.0
     */
    val weakRef: WeakRef<Tuple>
        get() {
            var ref = _weakRef
            if (ref == null) {
                ref = WeakRef(this)
                _weakRef = ref
            }
            return ref
        }

    /**
     * The [TupleNumber] of the [Tuple].
     * @since 3.0
     */
    @JvmField
    val tupleNumber: TupleNumber = membersBook[TnMember.id] as TupleNumber

    /**
     * The next-version at which this tuple was superseded. `NULL`-sentinel indicates the tuple is the current _([Version.HEAD])_ state.
     * @since 3.0
     */
    var nextVersion: Long
        get() = membersBook[NextVersionMember.id] as? Long? ?: Version.HEAD.number
        set(value) {
            val members = this.membersBook
            if (members is HeapBook) {
                members.put(NextVersionMember.id, value)
            } else {
                throw illegalState("{${FAL}Members book is immutable, failed to set nextVersion")
            }
        }

    private var _nextTupleNumber: TupleNumber? = null

    /**
     * The [TupleNumber] of the next version.; `null` if [Version.HEAD].
     * @since 3.0
     */
    val nextTupleNumber: TupleNumber?
        get() {
            if (nextVersion >= Version.HEAD.number) return null
            var nextTn = _nextTupleNumber
            if (nextTn == null) {
                nextTn = TupleNumber.copy(tupleNumber, version = nextVersion)
                _nextTupleNumber = nextTn
            }
            return nextTn
        }

    private var _globalBookTn: TupleNumber? = null

    /**
     * The [TupleNumber] of the global book needed to decode this [Tuple]. This [TupleNumber] can be used to load the book from the storage. If being `null`, then no global book is needed for decoding.
     * @since 3.0
     */
    val globalBookTn: TupleNumber?
        get() {
            val globalBookTn = _globalBookTn
            if (globalBookTn != null) return globalBookTn
            val raw = getMember(GlobalBookFeatureNumber)
            if (raw is Long) {
                TODO("Use the global book number as feature-number, combine with storage-number from tuple, and with admin-catalog, book-collection, version is always HEAD, books are immutable and can not be versioned")
            }
            _globalBookTn = globalBookTn
            return globalBookTn
        }

    private var _id: String? = null

    /**
     * The custom feature identifier.
     * - If the feature-number is non-negative, returns the stringified feature-number.
     * - If the feature-number is negative, reads the custom identifier from [membersBook].
     * @since 3.0
     * @throws NakshaException with error [ILLEGAL_STATE] if the feature number
     */
    val id: String
        get() {
            var id: String? = _id
            if (id != null) return id
            id = membersBook[IdMember.id] as String?
            if (id != null) {
                _id = id
                return id
            }
            val featureNumber = tupleNumber.featureNumber
            id = if (featureNumber >= 0) featureNumber.toString() else {
                throw NakshaException(ILLEGAL_STATE, "Missing 'id' member for tuple with negative feature-number: $tupleNumber")
            }
            _id = id
            return id
        }

    /**
     * Tests if the `Tuple` contains the given member, optionally if it is of the desired type.
     * @param member The member to query, only uses the [Member.id].
     * @param dataType The data-type to test for.
     * @return `true` if the member exists and the value is of the correct type; `false` otherwise.
     * @since 3.0
     */
    @JvmOverloads
    fun hasMember(member: Member, dataType: MemberType? = null): Boolean {
        val isVirtual = member.id == StandardMembers.FeatureNumberMember.id ||
            member.id == StandardMembers.VersionMember.id ||
            member.id == StandardMembers.ActionMember.id
        if (isVirtual) {
            val value = rawMember(member) ?: return false
            return dataType == null || dataType.isInstance(value)
        }
        val index = membersBook.indexOfName(member.id)
        if (index < 0) return false
        return dataType == null || dataType.isInstance(membersBook.get(index))
    }

    /**
     * Resolve a physically stored member or one of the virtual tuple-number members.
     * Virtual members are deliberately not duplicated in [membersBook].
     */
    private fun rawMember(member: Member): Any? = when (member.id) {
        StandardMembers.FeatureNumberMember.id -> tupleNumber.featureNumber
        StandardMembers.VersionMember.id -> tupleNumber.version
        StandardMembers.ActionMember.id -> tupleNumber.action.intValue
        else -> membersBook[member.id]
    }

    /**
     * Get a String member.
     * @param member The member to query, only uses the [Member.id].
     * @return the value from the [membersBook] book or `null`, if the member is missing, the value is `null`, or not the requested type.
     * @since 3.0
     */
    fun getString(member: Member): String? = rawMember(member) as? String

    /**
     * Get a long member.
     * @param member The member to query, only uses the [Member.id].
     * @param alt The alternative to return.
     * @return the value from the [membersBook] book or [alt], if the member is missing, the value is `null`, or not the requested type.
     * @since 3.0
     */
    @JvmOverloads
    fun getLong(member: Member, alt: Long = 0L): Long =
        rawMember(member)?.let { v ->
            when (v) {
                is Long -> v
                is Number -> v.toLong()
                else -> alt
            }
        } ?: alt

    /**
     * Get an integer member.
     * @param member The member to query, only uses the [Member.id].
     * @param alt The alternative to return.
     * @return the value from the [membersBook] book or [alt], if the member is missing, the value is `null`, or not the requested type.
     * @since 3.0
     */
    @JvmOverloads
    fun getInt(member: Member, alt: Int = 0): Int =
        rawMember(member)?.let { v ->
            when (v) {
                is Int -> v
                is Number -> v.toInt()
                else -> alt
            }
        } ?: alt

    /**
     * Get a double member.
     * @param member The member to query, only uses the [Member.id].
     * @param alt The alternative to return.
     * @return the value from the [membersBook] book or [alt], if the member is missing, the value is `null`, or not the requested type.
     * @since 3.0
     */
    @JvmOverloads
    fun getDouble(member: Member, alt: Double = Double.NaN): Double =
        membersBook[member.id]?.let { v ->
            when (v) {
                is Double -> v
                is Number -> v.toDouble()
                else -> alt
            }
        } ?: alt

    /**
     * Get a boolean member.
     * @param member The member to query, only uses the [Member.id].
     * @param alt The alternative to return.
     * @return the value from the [membersBook] book or [alt], if the member is missing, the value is `null`, or not the requested type.
     * @since 3.0
     */
    @JvmOverloads
    fun getBoolean(member: Member, alt: Boolean = false): Boolean = membersBook[member.id] as? Boolean ?: alt

    /**
     * Get the raw value of the member.
     * @param member The member to query, only uses the [Member.id].
     * @return the value from the [membersBook] book or `null`, if the member is missing, the value is `null`, or not the requested type.
     * @since 3.0
     */
    fun getMember(member: Member): Any? {
        return when (val raw = rawMember(member)) {
            is String -> raw
            is Byte -> raw.toInt()
            is Short -> raw.toInt()
            is Int -> raw
            is Long -> raw
            is Float -> raw.toDouble()
            is Double -> raw
            is ByteArray -> raw
            is TupleNumber -> raw
            is SpGeometry -> raw
            is TagMap -> raw
            is TagList -> raw
            is PTypedArray<*> -> raw
            is PlatformList -> raw.proxy(PAnyArray::class)
            is PTypedMap<*, *> -> {
                // Detect geometry.
                // If noSpGeometry, we can't differ between a TagMap and a simple Object in raw JSON, so treat is as Object.
                val type = SpType.ofDefined(raw["type"] as String?)
                if (type != null) raw.proxy(type.klass) else raw
            }
            is PlatformMap -> {
                // Detect geometry.
                // If noSpGeometry, we can't differ between a TagMap and a simple Object in raw JSON, so treat is as Object.
                val type = SpType.ofDefined(map_get(raw, "type") as String?)
                if (type != null) raw.proxy(type.klass) else raw.proxy(PAnyMap::class)
            }
            else -> null
        }
    }

    /**
     * Get the byte-array value of the member.
     * @param member The member to query, only uses the [Member.id].
     * @return the value from the [membersBook] book or `null`, if the member is missing, the value is `null`, or not the requested type.
     * @since 3.0
     */
    fun getByteArray(member: Member): ByteArray? = membersBook[member.id] as ByteArray?

    /**
     * Get the [SpGeometry] value of the member.
     * @param member The member to query, only uses the [Member.id].
     * @return the value from the [membersBook] book or `null`, if the member is missing, the value is `null`, or not the requested type.
     * @since 3.0
     */
    fun getSpatial(member: Member): SpGeometry? {
        val raw = membersBook[member.id]
        if (raw is SpGeometry) return raw
        if (raw is ByteArray) return try { Naksha.decodeGeometry(raw) } catch (_: Exception) { null }
        if (raw is PlatformMap) return raw.proxy(SpGeometry::class)
        return null
    }

    /**
     * Get the [TagMap] value of the member.
     * @param member The member to query, only uses the [Member.id].
     * @return the value from the [membersBook] book or `null`, if the member is missing, the value is `null`, or not the requested type.
     * @since 3.0
     */
    fun getTagMap(member: Member): TagMap? {
        val raw = membersBook[member.id]
        if (raw is TagMap) return raw
        if (raw is PlatformMap) return raw.proxy(TagMap::class)
        return null
    }

    /**
     * Get the [TagList] value of the member.
     * @param member The member to query, only uses the [Member.id].
     * @return the value from the [membersBook] book or `null`, if the member is missing, the value is `null`, or not the requested type.
     * @since 3.0
     */
    fun getTagList(member: Member): TagList? {
        val raw = membersBook[member.id]
        if (raw is TagList) return raw
        if (raw is PTypedArray<*>) return raw.proxy(TagList::class)
        if (raw is PlatformList) return raw.proxy(TagList::class)
        if (raw is String) {
            val decoded = try { fromJSON(raw) } catch (_: Exception) { null }
            if (decoded is PlatformList) return decoded.proxy(TagList::class)
        }
        return null
    }

    /**
     * Decode this [Tuple] into an [object][PAnyMap].
     * @param globalBook The global book to use to decode the [Tuple]. Need to be loaded from the storage, if [globalBookTn] is not `null`.
     * @return the decoded [object][PAnyMap].
     * @since 3.0
     * @throws NakshaException if any error occurs.
     */
    @JvmOverloads
    fun decodeObject(globalBook: IBook? = null): PAnyMap {
        val rawBytes = if (isGzipped(featureBytes)) gzipInflate(featureBytes) else featureBytes
        val decoder = JbDecoder2(globalBook, membersBook)
        decoder.mapBytes(rawBytes)
        return decoder.toAnyObject()
    }

    /**
     * Decode this [Tuple] into [NakshaFeature].
     * @param globalBook The global book to use to decode the [Tuple]. Need to be loaded from the storage, if [globalBookTn] is not `null`.
     * @return the decoded [NakshaFeature].
     * @since 3.0
     * @throws NakshaException if any error occurs.
     */
    @JvmOverloads
    fun decodeFeature(globalBook: IBook? = null): NakshaFeature = decodeObject(globalBook).proxy(NakshaFeature::class)

    /**
     * Decode this [Tuple] into [NakshaCollection].
     * @param globalBook The global book to use to decode the [Tuple]. Need to be loaded from the storage, if [globalBookTn] is not `null`.
     * @return the decoded [NakshaCollection].
     * @since 3.0
     * @throws NakshaException if any error occurs.
     */
    @JvmOverloads
    fun decodeCollection(globalBook: IBook? = null): NakshaCollection = decodeObject(globalBook).proxy(NakshaCollection::class)

    /**
     * Decode this [Tuple] into [NakshaCatalog].
     * @param globalBook The global book to use to decode the [Tuple]. Need to be loaded from the storage, if [globalBookTn] is not `null`.
     * @return the decoded [NakshaCatalog].
     * @since 3.0
     * @throws NakshaException if any error occurs.
     */
    @JvmOverloads
    fun decodeCatalog(globalBook: IBook? = null): NakshaCatalog = decodeObject(globalBook).proxy(NakshaCatalog::class)

    /**
     * Decode this [Tuple] into [NakshaDatabase].
     * @param globalBook The global book to use to decode the [Tuple]. Need to be loaded from the storage, if [globalBookTn] is not `null`.
     * @return the decoded [NakshaDatabase].
     * @since 3.0
     * @throws NakshaException if any error occurs.
     */
    @JvmOverloads
    fun decodeDatabase(globalBook: IBook? = null): NakshaDatabase = decodeObject(globalBook).proxy(NakshaDatabase::class)
}

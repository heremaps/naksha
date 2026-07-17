@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Action
import naksha.base.AnyList
import naksha.base.AnyObject
import naksha.base.Int64
import naksha.base.ListProxy
import naksha.base.MapProxy
import naksha.base.Platform.PlatformCompanion.fromJSON
import naksha.base.Platform.PlatformCompanion.gzipDeflate
import naksha.base.Platform.PlatformCompanion.gzipInflate
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
import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.base.NakshaException
import naksha.base.TupleNumber
import naksha.base.Version
import naksha.model.objects.MemberType
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StandardMembers
import naksha.model.objects.XyzMembers
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
     * After encoding a [NakshaFeature] into a [Tuple] using [encodeFeature] method, the [previousTupleNumber] will be set by the encoder to the [naksha.base.TupleNumber] of the given feature; if it had any.
     *
     * This is metadata, it can as well be set manually, when a tuple is read from a storage.
     * @since 3.0
     */
    @JvmField var previousTupleNumber: TupleNumber? = null
) {

    companion object Tuple_C {
        /**
         * Encodes the given [NakshaFeature] into JBON2 bytes and members book.
         *
         * @param feature the feature to encode.
         * @param collection the collection for which to encode the feature; declares the members.
         * @param action the action to apply.
         * @param session the session for which to encode; declares the version.
         * @param globalBook the global book to use for encoding; if any.
         * @param atomic if atomic, an UPDATE/DELETE requires the feature's uuid to pin the prior version.
         * TODO: `atomic` is write-path-specific; reconsider moving it out of this general encoder.
         * @return the encoded feature bytes (JBON2, optionally GZIP-compressed).
         * @since 3.0
         * @throws naksha.base.NakshaException if any fatal error happens when encoding.
         */
        @JsStatic
        @JvmStatic
        fun encodeFeature(
            feature: NakshaFeature,
            collection: NakshaCollection,
            action: Action,
            session: IWriteSession,
            globalBook: IBook?,
            atomic: Boolean = true
        ): Tuple {
            val members = collection.useMembers()
            val processors = session.processors

            // Update the tuple-number.
            val tnMember = collection.useMember(StandardMembers.Tn)
            val colTn = tnMember.getTupleNumber(collection) ?:
                throw NakshaException(ILLEGAL_ARGUMENT, "The given collection does not have a valid tuple-number (uuid)")
            if (colTn.featureNumber > Int.MAX_VALUE || colTn.featureNumber < Int.MIN_VALUE) {
                throw NakshaException(ILLEGAL_ARGUMENT, "The given collection does have an invalid feature-number (uuid)")
            }
            // Read the current tuple-number of the feature; if any.
            val prevTn: TupleNumber? = collection.useMember(StandardMembers.Tn).getTupleNumber(feature)
            // The transaction version carries the VERSION sentinel (=3) in its low two action bits; each
            // feature records its OWN action (CREATE/UPDATE/DELETE) there so the row reads as live.
            val version = (session.useTransaction().version.number and Int64(-4)) or Int64(action.intValue)
            // Tuple-number a brand-new feature (no prior version) receives in this collection.
            fun freshTupleNumber(): TupleNumber = TupleNumber(
                colTn.databaseNumber,
                colTn.catalogNumber,
                colTn.featureNumber.toInt(),
                when (collection.id) {
                    Naksha.COLLECTIONS_COL_ID -> Int64(Naksha.collectionNumber(feature.id))
                    Naksha.CATALOGS_COL_ID -> Int64(Naksha.catalogNumber(feature.id))
                    else -> Naksha.featureNumber(feature.id)
                },
                version
            )
            val newTn: TupleNumber
            var isFork = false
            if (prevTn != null) {
                if (action == Action.CREATE) {
                    val target = freshTupleNumber()
                    val sameLocation = prevTn.databaseNumber == target.databaseNumber
                            && prevTn.catalogNumber == target.catalogNumber
                            && prevTn.collectionNumber == target.collectionNumber
                            && prevTn.featureNumber == target.featureNumber
                    if (sameLocation) {
                        throw NakshaException(ILLEGAL_ARGUMENT, "Invalid action CREATE given, the feature exists already (has a uuid)")
                    }
                    // Fork: preserve lineage in `origin`, then create freshly in the target collection.
                    isFork = true
                    val originUuid = feature.properties.xyz.uuid
                    if (originUuid != null) feature.properties.xyz.setRaw(XyzNs.ORIGIN, originUuid)
                    newTn = target
                } else {
                    newTn = TupleNumber.copy(prevTn, version)
                }
            } else {
                if (action != Action.VERSION && action != Action.CREATE && atomic) {
                    throw NakshaException(ILLEGAL_ARGUMENT, "Invalid action $action given, the feature does not exist (missing uuid)")
                }
                newTn = freshTupleNumber()
            }
            // Update the feature with its new tuple-number.
            tnMember.set(feature, newTn)
            // A freshly encoded feature is the current HEAD state, so its next-version is the HEAD
            // sentinel; history rows get their real next-version from the write SQL.
            collection.useMember(StandardMembers.NextVersion).set(feature, Version.HEAD.number)
            val globalBookTn: TupleNumber?
            if (globalBook != null) {
                if (newTn.databaseNumber != globalBook.databaseNumber || globalBook.featureNumber == null) {
                    throw NakshaException(ILLEGAL_ARGUMENT, "The given global book is not located in the same storage as the feature")
                }
                globalBookTn = TupleNumber.copy(newTn, storageNumber = globalBook.databaseNumber, featureNumber = globalBook.featureNumber)
            } else {
                globalBookTn = null
            }

            // Create the encoder with a custom member encoder.
            val encoder = JbEncoder2(globalBook)
            val membersBook = HeapBook(BookType.MEMBER_BOOK)
            // Pre-populate the members-book in canonical member order (useMembers()): blob member
            // references are positional indices into this book, and the reader rebuilds it in the same
            // order (PgRows.getTuple).
            for (i in 0 until members.size) { val m = members[i]; if (m != null) membersBook.put(m.name, null) }
            val handledMembers = mutableSetOf<String>()
            encoder.withMemberEncoder { path: Array<Any?>, pathEnd: Int, value: Any? ->
                // Find the member whose declared path matches the current position and redirect its
                // value into the members-book.
                members@ for (i in 0 until members.size) {
                    val member = members[i] ?: continue
                    // GlobalBookFeatureNumber is injected from the global book, not read from the
                    // feature, so it must not take part in path matching (it is set after encoding).
                    if (StandardMembers.GlobalBookFeatureNumber.isSameAs(member)) continue
                    val memberName = member.name
                    val memberPath = member.path
                    if (memberPath.size != pathEnd) continue
                    for (pi in 0 until pathEnd) {
                        if (path[pi] != memberPath[pi]) continue@members
                    }
                    // Path matches
                    var v = value
                    val procs = processors[memberName]
                    if (procs != null) {
                        for (proc in procs) {
                            v = proc.processMember(session, collection, feature, member, v)
                        }
                    }
                    // Coerce the value to the expected type.
                    v = FeatureMemberValues.coerce(v, member.dataType, feature.id, memberName)

                    // Store in membersBook.
                    handledMembers.add(memberName)
                    return@withMemberEncoder membersBook.put(memberName, v)
                }
                -1
            }

            // Encode the feature.
            val raw = encoder.buildTupleFromMap(feature)

            for (i in 0 until members.size) {
                val member = members[i] ?: continue
                if (StandardMembers.GlobalBookFeatureNumber.isSameAs(member)) continue
                val memberName = member.name
                if (memberName in handledMembers) continue
                val procs = processors[memberName] ?: continue
                var v: Any? = null
                for (proc in procs) {
                    v = proc.processMember(session, collection, feature, member, v)
                }
                v = FeatureMemberValues.coerce(v, member.dataType, feature.id, memberName)
                membersBook.put(memberName, v)
            }
            for (i in 0 until members.size) {
                val member = members[i] ?: continue
                if (StandardMembers.ChangeCount.isSameAs(member)) {
                    membersBook.put(member.name, feature.properties.xyz.changeCount + 1)
                    break
                }
            }
            // The global-book feature-number is not part of the feature itself; inject it into the
            // members-book when a global book was provided.
            if (globalBookTn != null) membersBook.put(StandardMembers.GlobalBookFeatureNumber.name, globalBookTn.featureNumber)

            // A fork has no previous version in the target collection.
            val encodedPreviousTn = if (isFork) null else prevTn
            // Optionally GZIP.
            if (raw.size >= 1000) {
                val compressed = gzipDeflate(raw)
                if (compressed.size < raw.size) {
                    return Tuple(compressed, membersBook, encodedPreviousTn)
                }
            }
            return Tuple(raw, membersBook, encodedPreviousTn)
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
    val tupleNumber: TupleNumber = membersBook[StandardMembers.Tn.name] as TupleNumber

    /**
     * The next-version at which this tuple was superseded. `NULL`-sentinel indicates the tuple is the current _([Version.HEAD])_ state.
     * @since 3.0
     */
    var nextVersion: Int64
        get() = membersBook[StandardMembers.NextVersion.name] as Int64? ?: Version.HEAD.number
        set(version: Int64) {
            val members = this.membersBook
            if (members is HeapBook) {
                members.put(StandardMembers.NextVersion.name, version)
            } else {
                throw NakshaException(ILLEGAL_STATE, "Members book is immutable, failed to set nextVersion")
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
            val raw = getMember(StandardMembers.GlobalBookFeatureNumber)
            if (raw is Int64 || raw is Long) {
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
            id = membersBook[StandardMembers.Id.name] as String?
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
     * @param member The member to query, only uses the [Member.name].
     * @param dataType The data-type to test for.
     * @return `true` if the member exists and the value is of the correct type; `false` otherwise.
     * @since 3.0
     */
    @JvmOverloads
    fun hasMember(member: Member, dataType: MemberType? = null): Boolean {
        val isVirtual = member.name == StandardMembers.FeatureNumber.name ||
            member.name == StandardMembers.Version.name ||
            member.name == StandardMembers.Action.name
        if (isVirtual) {
            val value = rawMember(member) ?: return false
            return dataType == null || dataType.isInstance(value)
        }
        val index = membersBook.indexOfName(member.name)
        if (index < 0) return false
        return dataType == null || dataType.isInstance(membersBook.get(index))
    }

    /**
     * Resolve a physically stored member or one of the virtual tuple-number members.
     * Virtual members are deliberately not duplicated in [membersBook].
     */
    private fun rawMember(member: Member): Any? = when (member.name) {
        StandardMembers.FeatureNumber.name -> tupleNumber.featureNumber
        StandardMembers.Version.name -> tupleNumber.version
        StandardMembers.Action.name -> tupleNumber.action.intValue
        else -> membersBook[member.name]
    }

    /**
     * Get a String member.
     * @param member The member to query, only uses the [Member.name].
     * @return the value from the [membersBook] book or `null`, if the member is missing, the value is `null`, or not the requested type.
     * @since 3.0
     */
    fun getString(member: Member): String? = rawMember(member) as? String

    /**
     * Get a long member.
     * @param member The member to query, only uses the [Member.name].
     * @param alt The alternative to return.
     * @return the value from the [membersBook] book or [alt], if the member is missing, the value is `null`, or not the requested type.
     * @since 3.0
     */
    @JvmOverloads
    fun getLong(member: Member, alt: Int64 = Int64(0L)): Int64 =
        rawMember(member)?.let { v ->
            when (v) {
                is Int64 -> v
                is Long -> Int64(v)
                is Number -> Int64(v.toLong())
                else -> alt
            }
        } ?: alt

    /**
     * Get an integer member.
     * @param member The member to query, only uses the [Member.name].
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
     * @param member The member to query, only uses the [Member.name].
     * @param alt The alternative to return.
     * @return the value from the [membersBook] book or [alt], if the member is missing, the value is `null`, or not the requested type.
     * @since 3.0
     */
    @JvmOverloads
    fun getDouble(member: Member, alt: Double = Double.NaN): Double =
        membersBook[member.name]?.let { v ->
            when (v) {
                is Double -> v
                is Number -> v.toDouble()
                else -> alt
            }
        } ?: alt

    /**
     * Get a boolean member.
     * @param member The member to query, only uses the [Member.name].
     * @param alt The alternative to return.
     * @return the value from the [membersBook] book or [alt], if the member is missing, the value is `null`, or not the requested type.
     * @since 3.0
     */
    @JvmOverloads
    fun getBoolean(member: Member, alt: Boolean = false): Boolean = membersBook[member.name] as? Boolean ?: alt

    /**
     * Get the raw value of the member.
     * @param member The member to query, only uses the [Member.name].
     * @return the value from the [membersBook] book or `null`, if the member is missing, the value is `null`, or not the requested type.
     * @since 3.0
     */
    fun getMember(member: Member): Any? {
        return when (val raw = rawMember(member)) {
            is String -> raw
            is Int64 -> raw
            is Byte -> Int64(raw.toInt())
            is Short -> Int64(raw.toInt())
            is Int -> Int64(raw)
            is Long -> Int64(raw)
            is Float -> raw.toDouble()
            is Double -> raw
            is ByteArray -> raw
            is TupleNumber -> raw
            is SpGeometry -> raw
            is TagMap -> raw
            is TagList -> raw
            is ListProxy<*> -> raw
            is PlatformList -> raw.proxy(AnyList::class)
            is MapProxy<*, *> -> {
                // Detect geometry.
                // If noSpGeometry, we can't differ between a TagMap and a simple Object in raw JSON, so treat is as Object.
                val type = SpType.ofDefined(raw["type"] as String?)
                if (type != null) raw.proxy(type.klass) else raw
            }
            is PlatformMap -> {
                // Detect geometry.
                // If noSpGeometry, we can't differ between a TagMap and a simple Object in raw JSON, so treat is as Object.
                val type = SpType.ofDefined(map_get(raw, "type") as String?)
                if (type != null) raw.proxy(type.klass) else raw.proxy(AnyObject::class)
            }
            else -> null
        }
    }

    /**
     * Get the byte-array value of the member.
     * @param member The member to query, only uses the [Member.name].
     * @return the value from the [membersBook] book or `null`, if the member is missing, the value is `null`, or not the requested type.
     * @since 3.0
     */
    fun getByteArray(member: Member): ByteArray? = membersBook[member.name] as ByteArray?

    /**
     * Get the [SpGeometry] value of the member.
     * @param member The member to query, only uses the [Member.name].
     * @return the value from the [membersBook] book or `null`, if the member is missing, the value is `null`, or not the requested type.
     * @since 3.0
     */
    fun getSpatial(member: Member): SpGeometry? {
        val raw = membersBook[member.name]
        if (raw is SpGeometry) return raw
        if (raw is ByteArray) return try { Naksha.decodeGeometry(raw) } catch (_: Exception) { null }
        if (raw is PlatformMap) return raw.proxy(SpGeometry::class)
        return null
    }

    /**
     * Get the [TagMap] value of the member.
     * @param member The member to query, only uses the [Member.name].
     * @return the value from the [membersBook] book or `null`, if the member is missing, the value is `null`, or not the requested type.
     * @since 3.0
     */
    fun getTags(member: Member): TagMap? {
        val raw = membersBook[member.name]
        if (raw is TagMap) return raw
        if (raw is PlatformMap) return raw.proxy(TagMap::class)
        return null
    }

    /**
     * Get the [TagList] value of the member.
     * @param member The member to query, only uses the [Member.name].
     * @return the value from the [membersBook] book or `null`, if the member is missing, the value is `null`, or not the requested type.
     * @since 3.0
     */
    fun getTagList(member: Member): TagList? {
        val raw = membersBook[member.name]
        if (raw is TagList) return raw
        if (raw is ListProxy<*>) return raw.proxy(TagList::class)
        if (raw is PlatformList) return raw.proxy(TagList::class)
        if (raw is String) {
            val decoded = try { fromJSON(raw) } catch (_: Exception) { null }
            if (decoded is PlatformList) return decoded.proxy(TagList::class)
        }
        return null
    }

    /**
     * Get the set value of the member.
     * @param member The member to query, only uses the [Member.name].
     * @return the value from the [membersBook] book or `null`, if the member is missing, the value is `null`, or not the requested type.
     * @since 3.0
     */
    fun getSet(member: Member): ListProxy<*>? {
        val raw = membersBook[member.name]
        if (raw is ListProxy<*>) return raw
        if (raw is PlatformList) return raw.proxy(AnyList::class)
        return null
    }

    /**
     * Decode this [Tuple] into a [NakshaFeature].
     * @param globalBook The global book to use to decode the [Tuple]. Need to be loaded from the storage, if [globalBookTn] is not `null`.
     * @return the decoded [NakshaFeature].
     * @since 3.0
     * @throws NakshaException if any error occurs.
     */
    fun decodeFeature(globalBook: IBook?): NakshaFeature { // TODO: Java: After switching back to Java, we can allow arbitrary return types.
        val rawBytes = if (isGzipped(featureBytes)) gzipInflate(featureBytes) else featureBytes
        val decoder = JbDecoder2(globalBook, membersBook)
        decoder.mapBytes(rawBytes)
        return decoder.toAnyObject().proxy(NakshaFeature::class)
    }

    /**
     * Like [decodeFeature], but also overlays the XYZ metadata namespace (`app_id`, `author`, timestamps, …)
     * and `tags` from the tuple's dedicated columns, which the feature blob does not carry.
     * @param globalBook the global book to use to decode the [Tuple]; if any.
     * @return the decoded [NakshaFeature] with its XYZ namespace populated.
     * @since 3.0
     */
    @JvmOverloads
    fun toNakshaFeature(globalBook: IBook? = null): NakshaFeature {
        val feature = decodeFeature(globalBook)
        if (feature.getAs("id", String::class) == null) feature.id = id
        feature.properties.xyz = XyzNs.fromTuple(this)
        val tags = getTagList(XyzMembers.XyzTags)
        if (tags != null) feature.properties.xyz.tags = tags
        return feature
    }
}

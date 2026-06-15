@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.AnyList
import naksha.base.AnyObject
import naksha.base.Int64
import naksha.base.ListProxy
import naksha.base.MapProxy
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
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.objects.MemberType
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StandardMembers
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
     * After encoding a [NakshaFeature] into a [Tuple] using [encodeFeature] method, the [previousTupleNumber] will be set by the encoder to the [TupleNumber] of the given feature; if it had any.
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
         * @return the encoded feature bytes (JBON2, optionally GZIP-compressed).
         * @since 3.0
         * @throws NakshaException if any fatal error happens when encoding.
         */
        @JsStatic
        @JvmStatic
        fun encodeFeature(
            feature: NakshaFeature,
            collection: NakshaCollection,
            action: Action,
            session: IWriteSession,
            globalBook: IBook?
        ): Tuple {
            val members = collection.useMembers()
            val processors = session.processors()

            // Update the tuple-number.
            val tnMember = collection.useMember(StandardMembers.Tn)
            val colTn = tnMember.getTupleNumber(collection) ?:
                throw NakshaException(ILLEGAL_ARGUMENT, "The given collection does not have a valid tuple-number (uuid)")
            if (colTn.featureNumber > Int.MAX_VALUE || colTn.featureNumber < Int.MIN_VALUE) {
                throw NakshaException(ILLEGAL_ARGUMENT, "The given collection does have an invalid feature-number (uuid)")
            }
            // Read the current tuple-number of the feature; if any.
            val prevTn: TupleNumber? = collection.useMember(StandardMembers.Tn).getTupleNumber(feature)
            val newTn: TupleNumber
            if (prevTn != null) {
                if (action != Action.VERSION && action == Action.CREATE) {
                    throw NakshaException(ILLEGAL_ARGUMENT, "Invalid action CREATE given, the feature exists already (has a uuid)")
                }
                newTn = TupleNumber.copy(prevTn, session.useTransaction().version.number)
            } else {
                if (action != Action.VERSION && action != Action.CREATE) {
                    throw NakshaException(ILLEGAL_ARGUMENT, "Invalid action $action given, the feature does not exist (missing uuid)")
                }
                newTn = TupleNumber(
                    // The feature is stored in the same database as the collection it is inserted into.
                    colTn.databaseNumber,
                    // The feature is stored in the same catalog as the collection it is inserted into.
                    colTn.catalogNumber,
                    // The feature-number of the collection is the collection-number of the feature we want to store in the collection.
                    colTn.featureNumber.toInt(),
                    // The feature-number of the feature, either the `id` is a feature-number or it is calculated form hashing the `id`.
                    Naksha.featureNumber(feature.id),
                    // The version is the one of the transaction.
                    session.useTransaction().version.number
                )
            }
            // Update the feature with its new tuple-number.
            tnMember.set(feature, newTn)
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
            encoder.withMemberEncoder { path: Array<Any?>, pathEnd: Int, value: Any? ->
                // Build the current path key from the encoder's path.
                members@ for (i in 0 until members.size) {
                    val member = members[i] ?: continue
                    if (StandardMembers.GlobalBookFeatureNumber.isSameAs(member)) {
                        return@withMemberEncoder if (globalBookTn != null) membersBook.put(member.name, globalBookTn.featureNumber) else -1
                    }
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
                    v = FeatureMemberValues.coerce(value, member.dataType, feature.id, memberName)

                    // Store in membersBook.
                    return@withMemberEncoder membersBook.put(memberName, v)
                }
                -1
            }

            // Encode the feature.
            val raw = encoder.buildTupleFromMap(feature)

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
    val tupleNumber: TupleNumber = membersBook[StandardMembers.Tn.name] as TupleNumber

    /**
     * The next-version at which this tuple was superseded. `NULL`-sentinel indicates the tuple is the current _([Version.HEAD])_ state.
     * @since 3.0
     */
    var nextVersion: Int64
        get() = membersBook[StandardMembers.NextVersion.name] as Int64
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
        val index = membersBook.indexOfName(member.name)
        if (index < 0) return false
        if (dataType == null) return true
        val value = membersBook.get(index)
        return dataType.isInstance(value)
    }

    /**
     * Get a String member.
     * @param member The member to query, only uses the [Member.name].
     * @return the value from the [membersBook] book or `null`, if the member is missing, the value is `null`, or not the requested type.
     * @since 3.0
     */
    fun getString(member: Member): String? = membersBook[member.name] as? String

    /**
     * Get a long member.
     * @param member The member to query, only uses the [Member.name].
     * @param alt The alternative to return.
     * @return the value from the [membersBook] book or [alt], if the member is missing, the value is `null`, or not the requested type.
     * @since 3.0
     */
    @JvmOverloads
    fun getLong(member: Member, alt: Int64 = Int64(0L)): Int64 =
        membersBook[member.name]?.let { v ->
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
        membersBook[member.name]?.let { v ->
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
        return when (val raw = membersBook[member.name]) {
            is String -> raw
            is Int64 -> raw
            is Byte -> Int64(raw.toInt())
            is Short -> Int64(raw.toInt())
            is Int -> Int64(raw)
            is Long -> Int64(raw)
            is Float -> raw.toDouble()
            is Double -> raw
            is ByteArray -> raw
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
        if (raw is PlatformList) return raw.proxy(TagList::class)
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
}

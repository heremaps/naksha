@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Action
import naksha.base.AnyList
import naksha.base.AnyObject
import naksha.base.Guid
import naksha.base.Id
import naksha.base.ListProxy
import naksha.base.MapProxy
import naksha.base.Platform.PlatformCompanion.fromJSON
import naksha.base.Platform.PlatformCompanion.gzipDeflate
import naksha.base.Platform.PlatformCompanion.gzipInflate
import naksha.base.PlatformList
import naksha.base.PlatformMap
import naksha.base.PlatformObject
import naksha.base.PlatformMapApi.PlatformMapApiCompanion.map_get
import naksha.base.PlatformMapApi.PlatformMapApiCompanion.map_set
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
import naksha.base.Platform
import naksha.base.Platform.PlatformCompanion.UNDEFINED
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.PlatformListApi.PlatformListApiCompanion.array_get
import naksha.base.PlatformListApi.PlatformListApiCompanion.array_set
import naksha.base.TupleNumber
import naksha.base.Version
import naksha.base.illegalArg
import naksha.base.illegalState
import naksha.base.internalError
import naksha.base.proxy
import naksha.geo.GeoUtil.GeoUtil_C.fromTWKB
import naksha.model.objects.JsonPath
import naksha.model.objects.MemberList
import naksha.model.objects.MemberType
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaTx
import naksha.model.objects.StandardMembers
import naksha.model.objects.StandardMembers.StandardMembers_C.CHANGE_COUNT
import naksha.model.objects.StandardMembers.StandardMembers_C.GLOBAL_BOOK_FN
import naksha.model.objects.StandardMembers.StandardMembers_C.NEXT_VERSION
import naksha.model.objects.StandardMembers.StandardMembers_C.ORIGIN
import naksha.model.objects.StandardMembers.StandardMembers_C.TN
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass

/**
 * A tuple represents a specific immutable state of a feature in binary encoding.
 * @since 3.0
 */
@JsExport
data class Tuple @JvmOverloads constructor(
    /**
     * The uncompressed `JBON` binary of the Feature, to be read with `JbonDecoder`.
     * @since 3.0
     */
    val featureBytes: ByteArray,

    /**
     * The reference to a reader for the member's book.
     *
     * This can either be just a small instance that decodes the data from the [featureBytes] or it can be some special _HEAP_ decoded members book provided by storage at read time. Generally, this Contains the values of the [Member].
     * @since 3.0
     */
    val membersBook: IBook,

    /**
     * After encoding an object into a [Tuple] using [encodeFeature] method, the [previousTupleNumber] will be set in the generated [Tuple]. It will only be set by the encoder, if the given object contains a [TupleNumber][naksha.base.TupleNumber] of the state it had before being modified. In the XYZ specification this information was historically located in the jsonpath `properties->@ns:com:here:xyz->uuid`, but with modern `lib-base` support, the position can be freely selected.
     *
     * This is metadata, it can as well be set manually, when a tuple is read from a storage. It is not significant for the storage itself.
     * @since 3.0
     */
    @JvmField var previousTupleNumber: TupleNumber? = null
) {

    // TODO: We need to extend the `Tuple` class.
    //       We need to add a `val bytes: ByteArray` member.
    //       When being read, the tuple must be, if not yet done, encoded into a single binary.
    //       We should still support `featureBytes`. Its just another option to keep the object at the JVM heap.
    //       We need to support both modes. Ones the `bytes` have been generated, `featureBytes` must be extracted from `bytes`.
    //       Generally we want to keep data on the heap automatically compacted, when not actively used, so as a single compact binary.
    //       Therefore, we should have some internal `compact()` method that ensures this, it should replace the members book
    //       with a pseudo instance, that just decodes from the `bytes`. The `featureBytes` in that case are as well just decoded
    //       from the `bytes` _(this is actually just a section of the bytes)_.
    //       The concept is, when being returned from the storage, we have the on HEAP representation, the compaction should happen
    //       when not being accessed for some time, using a background job, or when explicitly requested by the application.
    //       Applications that want to store the tuple as binary or transfer it over the network, require the single binary !!!

    companion object Tuple_C {
        /**
         * Encodes the given [NakshaFeature] into `JBON2` bytes.
         *
         * @param feature the feature to encode.
         * @param collection the collection for which to encode the feature; declares the members.
         * @param session the session for which to encode; declares the version.
         * @param globalBook the global book to use for encoding; if any.
         * @param action an action the client wants to perform; if `null`, auto-decided.
         * @param atomic a hint if the operation **must** be atomic; if `null`, auto-decided.
         * @return the encoded feature bytes (JBON2, optionally GZIP-compressed).
         * @since 3.0
         * @throws NakshaException if any error happens when encoding.
         */
        @JsStatic
        @JvmStatic
        @JvmOverloads
        fun encodeFeature(
            feature: NakshaFeature,
            collection: NakshaCollection,
            session: IWriteSession,
            globalBook: IBook? = null,
            action: Action? = null,
            atomic: Boolean? = null,
        ): Tuple {
            return encodeRaw(
                raw = feature,
                databaseNumber = collection.databaseNumber,
                catalogNumber = collection.catalogNumber,
                collectionNumber = collection.collectionNumber,
                memberList = collection.validatedMembers,
                session = session,
                globalBook = globalBook,
                processors = session.processors,
                requestedAction = action,
                atomic = atomic,
            )
        }

        /**
         * Encodes the given object into `JBON2` bytes.
         *
         * @param feature the feature to encode.
         * @param collection the collection for which to encode the feature; declares the members.
         * @param version the version in which to encode.
         * @param processors the [IMemberProcessor]'s to process extracted [Member]'s; if any.
         * @param globalBook the global book to use for encoding; if any.
         * @param action an action the client wants to perform; if `null`, auto-decided.
         * @param atomic a hint if the operation **must** be atomic; if `null`, auto-decided.
         * @return the encoded feature bytes (JBON2, optionally GZIP-compressed).
         * @since 3.0
         * @throws NakshaException if any error happens when encoding.
         */
        @JsStatic
        @JvmStatic
        @JvmOverloads
        fun encodeObject(
            feature: AnyObject,
            collection: NakshaCollection,
            version: Long,
            processors: MemberProcessorMap? = null,
            globalBook: IBook? = null,
            action: Action? = null,
            atomic: Boolean? = null,
        ): Tuple {
            return encodeRaw(
                raw = feature,
                databaseNumber = collection.databaseNumber,
                catalogNumber = collection.catalogNumber,
                collectionNumber = collection.collectionNumber,
                memberList = collection.validatedMembers,
                session = null,
                version = version,
                globalBook = globalBook,
                processors = processors,
                requestedAction = action,
                atomic = atomic,
            )
        }

        /**
         * Encode the given raw object into `JBON2` bytes.
         * @param raw the raw `JSON` object.
         * @param databaseNumber the database-number of the database for which to encode the object.
         * @param catalogNumber the catalog-number of the catalog for which to encode the object.
         * @param collectionNumber the collection-number of the collection for which to encode the object.
         * @param memberList the [MemberList] to use for encoding _(separates members into the storage book of the `JBON`)_.
         * @param session the [write-session][IWriteSession] in which context this is performed; optional, used only by [member processors][IMemberProcessor].
         * @param version the version in which to encode the raw object; if a session is given, it is read from it; otherwise this must be provided explicitly.
         * @param globalBook the global book to use for data compression; defaults to `null` _(no global book compression)_.
         * @param processors the processors to use to process members; defaults to `session.processors` if a session is given; otherwise `null` _(no processors)_.
         * @param requestedAction an action the client wants to perform; defaults to `null` meaning auto-detected.
         * @param atomic a hint if the operation **must** be atomic; defaults to `null` meaning auto-detected.
         */
        @JsStatic
        @JvmStatic
        @JvmOverloads
        fun encodeRaw(
            raw: AnyObject,
            databaseNumber: Long,
            catalogNumber: Int,
            collectionNumber: Int,
            memberList: MemberList,
            session: IWriteSession? = null,
            version: Long = session?.version?.number ?: throw illegalArg("Missing version"),
            globalBook: IBook? = null,
            processors: MemberProcessorMap? = session?.processors,
            requestedAction: Action? = null,
            atomic: Boolean? = null,
        ): Tuple {
            // Find members.
            val idMember = memberList.useMember(StandardMembers.Id)
            val tnMember = memberList.useMember(StandardMembers.Tn) // uuid
            val nextVersionMember = memberList.useMember(StandardMembers.NextVersion)
            val globalBookFnMember = memberList.useMember(StandardMembers.GlobalBookFeatureNumber)
            val originMember = memberList.findMember(StandardMembers.Origin)

            // Create feature with modified members copied.
            val f = shallowCopy(raw, arrayOf(tnMember.path, nextVersionMember.path, globalBookFnMember.path, originMember?.path))

            // Read members.
            val id = Id(idMember.readString(raw) ?: throw illegalArg("Missing 'id' in NakshaFeature"))
            var prevTn: TupleNumber?
            var origin: String? = originMember?.readString(raw)
            val rawUuid = raw.getPath(tnMember.path)
            val uuidParts = (rawUuid as? String)?.split(":")?.size

            if (uuidParts != null &&
                uuidParts != TupleNumber.ALL_PARTS &&
                uuidParts != Guid.ALL_PARTS &&
                uuidParts != Guid.ID_ONLY_PARTS
            ) {
                //UUID is from another source not Naksha V3, so this is treated as a new feature
                prevTn = null
                origin = rawUuid
            } else {
                prevTn = tnMember.readTupleNumber(raw)
            }

            // Detect CREATE, UPDATE, fork, and update.
            val action: Action
            if (prevTn != null) {
                if (databaseNumber != prevTn.databaseNumber ||
                    catalogNumber != prevTn.catalogNumber ||
                    collectionNumber != prevTn.collectionNumber ||
                    id.number != prevTn.featureNumber)
                {
                    // FORK: Client copied a feature from another collection or changed the `id`.
                    origin = prevTn.toString()
                    prevTn = null
                    action = when (requestedAction) {
                        Action.CREATE -> Action.CREATE
                        Action.UPDATE -> throw illegalArg("Requested action UPDATE, but client provided a foreign object")
                        Action.DELETE -> Action.DELETE
                        else -> Action.CREATE
                    }
                } else { // Client modified an existing state.
                    action = when (requestedAction) {
                        Action.CREATE -> {
                            if (prevTn.action != Action.DELETE) throw illegalArg("Requested ${if(atomic!=true) "" else "atomic "} action CREATE, but client provided a modified object")
                            Action.CREATE
                        }
                        Action.UPDATE -> Action.UPDATE
                        Action.DELETE -> Action.DELETE
                        else -> Action.UPDATE
                    }
                }
            } else {
                action = when (requestedAction) {
                    Action.CREATE-> Action.CREATE
                    Action.UPDATE -> if (atomic != true) Action.UPDATE else throw illegalArg("Requested atomic action UPDATE, but client did not provide a 'uuid' (expected state)")
                    Action.DELETE -> Action.DELETE
                    else -> Action.CREATE // When not decided, we assume CREATE
                }
            }

            // The transaction version carries the VERSION sentinel (=3) in its low two action bits; each
            // feature records its OWN action (CREATE/UPDATE/DELETE) there so the row reads as live.
            val newTn = TupleNumber(
                databaseNumber,
                catalogNumber,
                collectionNumber,
                id.number,
                (version and -4L) or action.longValue
            )

            val globalBookTn: TupleNumber?
            if (globalBook != null) {
                if (newTn.databaseNumber != globalBook.databaseNumber || globalBook.featureNumber == null) {
                    throw illegalArg("The given global book is not located in the same storage as the feature")
                }
                globalBookTn = TupleNumber.copy(newTn, storageNumber = globalBook.databaseNumber, featureNumber = globalBook.featureNumber)
            } else {
                globalBookTn = null
            }

            originMember?.write(f, origin)
            tnMember.write(f, newTn)
            nextVersionMember.write(f, UNDEFINED)
            globalBookFnMember.write(f, globalBookTn?.featureNumber ?: UNDEFINED)

            // Create the members book with member in the order as defined in the NakshaCollection.
            // This is important, because references are positional indices into the members book,
            // and the reader (PgRows.getTuple) restores the tuple in the same order.
            val membersBook = HeapBook(BookType.MEMBER_BOOK)
            members@ for (i in 0 until memberList.size) {
                val member = memberList[i] ?: throw illegalState("Member must not be null")
                val memberName = member.name
                if (member.isVirtual()) continue
                var memberValue: Any? = when (memberName) {
                    TN -> newTn
                    NEXT_VERSION -> null
                    ORIGIN -> origin
                    GLOBAL_BOOK_FN -> globalBookTn?.featureNumber
                    CHANGE_COUNT -> (member.readLong(f) ?: 0L) + 1L
                    else -> member.read(f)
                }
                val procs = processors?.get(memberName)
                if (procs != null) {
                    for (proc in procs) {
                        memberValue = proc.processMember(session, memberList, f, member, memberValue)
                    }
                }
                memberValue = coerce(memberValue, member.dataType, id, memberName)
                membersBook.put(memberName, memberValue)
            }

            // Create the encoder with a custom member encoder.
            val encoder = JbEncoder2(globalBook)
            encoder.withMemberEncoder { path: Array<Any?>, pathEnd: Int, _: Any? ->
                // Find the member whose declared path matches the current position and redirect its value into the members-book.
                members@ for (i in 0 until memberList.size) {
                    val member = memberList[i] ?: continue
                    if (member.isVirtual()) continue
                    val memberName = member.name
                    val memberPath = member.path
                    if (memberPath.size != pathEnd) continue
                    for (pi in 0 until pathEnd) {
                        if (path[pi] != memberPath[pi]) continue@members
                    }
                    // Path matches.
                    val memberIndex = membersBook.indexOfName(memberName)
                    if (memberIndex < 0) throw internalError("Member index should not be negative at this point")
                    return@withMemberEncoder memberIndex
                }
                -1
            }

            // Encode the feature.
            val featureBytes = encoder.buildTupleFromMap(f)

            // Optionally GZIP.
            if (featureBytes.size >= 1000) {
                val compressed = gzipDeflate(featureBytes)
                if (compressed.size < featureBytes.size) {
                    return Tuple(compressed, membersBook, prevTn)
                }
            }
            return Tuple(featureBytes, membersBook, prevTn)
        }

        /**
         * Minimal copy of [NakshaFeature].
         *
         * The method creates shallow _(not recursive)_ root copy of the given feature. Then it iterates all given paths and makes yet another shallow copy of each given path in order. The copy along the given `path` is replaced by a shallow copy, everything else is shared with [NakshaFeature].
         * @param original the original feature.
         * @param paths the paths to make a shallow copy of.
         * @return a new feature that shares everything, but the given path with `original`.
         * @since 3.0
         */
        @Deprecated(message = "This method is going to be removed as soon as we have real read-only support with real copy")
        fun <T: AnyObject> shallowCopy(original: T, paths: Array<JsonPath?>): T {
            val copy_root = Platform.copy(original.platformObject(), false) as PlatformMap
            for (path in paths) {
                if (path == null) continue
                var origin_current: Any? = original.platformObject()
                var copy_current: Any? = copy_root
                for (i in 0 until path.size - 1) {
                    val key = path[i]
                    val origin_parent = origin_current
                    val copy_parent = copy_current
                    if (key is String && origin_parent is PlatformMap && copy_parent is PlatformMap) {
                        val origin_child: Any? = map_get(origin_parent, key)
                        var copy_child: Any? = map_get(copy_parent, key)
                         if (copy_child === origin_child) {
                             // No valid copy has been made yet, do it now.
                             copy_child = Platform.copy(origin_child, false)
                             map_set(copy_parent, key, copy_child)
                        }
                        origin_current = origin_child
                        copy_current = copy_child
                        continue
                    }
                    if (key is Number && origin_parent is PlatformList && copy_parent is PlatformList) {
                        val origin_child: Any? = array_get(origin_parent, key.toInt())
                        var copy_child: Any? = array_get(copy_parent, key.toInt())
                         if (copy_child === origin_child) {
                             // No valid copy has been made yet, do it now.
                             copy_child = Platform.copy(origin_child, false)
                             array_set(copy_parent, key.toInt(), copy_child)
                        }
                        origin_current = origin_child
                        copy_current = copy_child
                        continue
                    }
                    // We reached the end of the path, no more copies can be made.
                    // The path contains something that is no map or list.
                    break
                }
            }
            return copy_root.proxy(original::class)
        }

        /** Copy every container along [path], leaving unrelated feature data shared. */
        fun copyOnWrite(original: NakshaFeature, path: JsonPath): NakshaFeature {
            val root = Platform.copy(original.platformObject(), false) as PlatformMap
            var current: PlatformObject = root
            for (i in 0 until path.size) {
                val key = path[i]
                val parent = current
                val child = when (parent) {
                    is PlatformMap if key is String -> map_get(parent, key)
                    is PlatformList if key is Number -> array_get(parent, key.toInt())
                    else -> null
                }
                if (child !is PlatformMap && child !is PlatformList) break
                val childCopy = Platform.copy(child, false) as PlatformObject
                if (parent is PlatformMap) map_set(parent, key as String, childCopy)
                else array_set(parent as PlatformList, (key as Number).toInt(), childCopy)
                current = childCopy
            }
            return root.proxy(NakshaFeature::class)
        }

        private fun isGzipped(bytes: ByteArray): Boolean =
            bytes.size >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()

        @Suppress("unused")
        private fun isJbon2(bytes: ByteArray): Boolean =
            bytes.size >= 4 &&
                    bytes[0] == JB2_MAGIC[0] && bytes[1] == JB2_MAGIC[1] &&
                    bytes[2] == JB2_MAGIC[2] && bytes[3] == JB2_MAGIC[3]

        @Suppress("unused")
        private fun isJson(bytes: ByteArray): Boolean {
            val b = bytes[0]
            return b == 0x7B.toByte() || b == 0x5B.toByte() ||
                    b == 0x20.toByte() || b == 0x09.toByte() ||
                    b == 0x0A.toByte() || b == 0x0D.toByte()
        }

        /**
         * Coerce a raw value to the type expected by the member.
         *
         * For [MemberType.SPATIAL], the value is expected to be a [SpGeometry] (or [naksha.geo.SpPoint]) which will be
         * encoded as TWKB bytes. For other types, the value is expected to already be the raw JSON value.
         *
         * @param value the raw value extracted from the feature.
         * @param type the expected [MemberType].
         * @param featureId the feature identifier (for logging).
         * @param memberName the member name (for logging).
         * @return the coerced value, or _null_ if coercion failed.
         */
        private fun coerce(value: Any?, type: MemberType, featureId: Id, memberName: String): Any? {
            if (value == null) return null
            return when (type) {
                MemberType.BOOLEAN -> coerceBoolean(value, featureId, memberName)
                MemberType.INT8 -> coerceInt8(value, featureId, memberName)
                MemberType.INT16 -> coerceInt16(value, featureId, memberName)
                MemberType.INT32 -> coerceInt32(value, featureId, memberName)
                MemberType.INT64 -> coerceInt64(value, featureId, memberName)
                MemberType.FLOAT32 -> coerceFloat32(value, featureId, memberName)
                MemberType.FLOAT64 -> coerceFloat64(value, featureId, memberName)
                MemberType.STRING -> coerceString(value, featureId, memberName)
                MemberType.BYTE_ARRAY -> coerceByteArray(value, featureId, memberName)
                MemberType.SPATIAL -> coerceSpatial(value, featureId, memberName)
                MemberType.TAG_MAP -> coerceTagMap(value, featureId, memberName)
                MemberType.TAG_LIST -> coerceTagList(value, featureId, memberName)
                MemberType.TUPLE_NUMBER -> coerceTupleNumber(value, featureId, memberName)
                else -> {
                    warnMismatch(featureId, memberName, type.toString(), value)
                    null
                }
            }
        }

        // -------------------------------------------------------------------------
        // Individual coercion implementations
        // -------------------------------------------------------------------------

        private fun coerceBoolean(value: Any, featureId: Id, memberName: String): Boolean? = when (value) {
            is Boolean -> value
            else -> { warnMismatch(featureId, memberName, "Boolean", value); null }
        }

        private fun coerceInt8(value: Any, featureId: Id, memberName: String): Byte? {
            val asLong = numberToLongOrNull(value) ?: return null.also { warnMismatch(featureId, memberName, "Byte", value) }
            if (asLong !in Byte.MIN_VALUE.toLong()..Byte.MAX_VALUE.toLong()) {
                warnMismatch(featureId, memberName, "Byte", value)
                return null
            }
            return asLong.toByte()
        }

        private fun coerceInt16(value: Any, featureId: Id, memberName: String): Short? {
            val asLong = numberToLongOrNull(value) ?: return null.also { warnMismatch(featureId, memberName, "Short", value) }
            if (asLong !in Short.MIN_VALUE.toLong()..Short.MAX_VALUE.toLong()) {
                warnMismatch(featureId, memberName, "Short", value)
                return null
            }
            return asLong.toShort()
        }

        private fun coerceInt32(value: Any, featureId: Id, memberName: String): Int? {
            val asLong = numberToLongOrNull(value) ?: return null.also { warnMismatch(featureId, memberName, "Int", value) }
            if (asLong !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                warnMismatch(featureId, memberName, "Int", value)
                return null
            }
            return asLong.toInt()
        }

        private fun coerceInt64(value: Any, featureId: Id, memberName: String): Long? = when (value) {
            is Int -> value.toLong()
            is Long -> value
            is Short -> value.toLong()
            is Byte -> value.toLong()
            is Double -> if (value.isFinite() && value == value.toLong().toDouble()) value.toLong()
                         else { warnMismatch(featureId, memberName, "Long", value); null }
            is Float -> if (value.isFinite() && value == value.toLong().toFloat()) value.toLong()
                        else { warnMismatch(featureId, memberName, "Long", value); null }
            else -> { warnMismatch(featureId, memberName, "Long", value); null }
        }

        private fun float32OrNull(value: Any?): Float? = when (value) {
            is Float -> value
            is Double -> value.toFloat()
            is Int -> value.toFloat()
            is Long -> value.toFloat()
            is Short -> value.toFloat()
            is Byte -> value.toFloat()
            else -> null
        }

        private fun coerceFloat32(value: Any, featureId: Id, memberName: String): Float? {
            val v = float32OrNull(value)
            if (v == null) warnMismatch(featureId, memberName, "Float", value)
            return v
        }

        private fun float64OrNull(value: Any?): Double? = when (value) {
            is Double -> value
            is Float -> value.toDouble()
            is Int -> value.toDouble()
            is Long -> value.toDouble()
            is Short -> value.toDouble()
            is Byte -> value.toDouble()
            else -> null
        }

        private fun coerceFloat64(value: Any, featureId: Id, memberName: String): Double? {
            val v = float64OrNull(value)
            if (v == null) warnMismatch(featureId, memberName, "Double", value)
            return v
        }

        private fun coerceString(value: Any, featureId: Id, memberName: String): String? = when (value) {
            is String -> value
            is Char, is CharSequence -> value.toString()
            else -> { warnMismatch(featureId, memberName, "String", value); null }
        }

        private fun coerceByteArray(value: Any, featureId: Id, memberName: String): ByteArray? = when (value) {
            is ByteArray -> value
            else -> { warnMismatch(featureId, memberName, "ByteArray", value); null }
        }

        private fun tupleNumberOrNull(value: Any?): TupleNumber? = when (value) {
            is TupleNumber -> value
            is String -> TupleNumber.fromStringOrGuid(value)
            is ByteArray -> TupleNumber.fromByteArray(value)
            else -> null
        }

        /**
         * Coerce a tuple-number value: an already-materialized [naksha.base.TupleNumber], or a string/byte-array encoding.
         */
        private fun coerceTupleNumber(value: Any, featureId: Id, memberName: String): TupleNumber? {
            val v = tupleNumberOrNull(value)
            if (v == null) warnMismatch(featureId, memberName, "TupleNumber", value)
            return v
        }

        /**
         * Coerce a spatial value to a [SpGeometry].
         */
        private fun coerceSpatial(value: Any, featureId: Id, memberName: String): SpGeometry? {
            val geometry = when (value) {
                is SpGeometry -> value
                is ByteArray -> fromTWKB(value)
                else -> value.proxy(SpGeometry::class)
            }
            if (geometry != null) return geometry
            warnMismatch(featureId, memberName, "SpGeometry", value);
            return null
        }

        private fun coerceTagMap(value: Any, featureId: Id, memberName: String): TagMap? {
            val tagMap = value.proxy(TagMap::class)
            if (tagMap != null) return tagMap
            warnMismatch(featureId, memberName, "TagMap", value)
            return null
        }

        private fun coerceTagList(value: Any, featureId: Id, memberName: String): TagList? {
            val list = when (value) {
                is TagList -> value
                is PlatformList -> value.proxy(TagList::class)
                is Array<*> -> TagList.fromArray(value)
                is List<*> -> TagList(value)
                else -> value.proxy(TagList::class)
            }
            if (list != null) return list
            warnMismatch(featureId, memberName, "tag_list", value)
            return null
        }

        /**
         * Convert the given number into [Long] or return `null`.
         * @param value the potential value.
         * @return either [Long] or `null`.
         * @since 3.0
         */
        private fun numberToLongOrNull(value: Any?): Long? = when (value) {
            is Byte -> value.toLong()
            is Short -> value.toLong()
            is Int -> value.toLong()
            is Long -> value
            is Float -> if (value.isFinite() && value == value.toLong().toFloat()) value.toLong() else null
            is Double -> if (value.isFinite() && value == value.toLong().toDouble()) value.toLong() else null
            else -> null
        }

        private fun warnMismatch(featureId: Id, memberName: String, expected: String, value: Any) {
            logger.warn("Member '$memberName' on feature '$featureId': expected $expected, got ${value::class.simpleName}")
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
    val tupleNumber: TupleNumber
        get() = membersBook[TN] as TupleNumber

    /**
     * The action of the [Tuple], actually similar to `tupleNumber.action`.
     * @since 3.0
     */
    val action: Action
        get() = tupleNumber.action

    /**
     * The version of the [Tuple], actually similar to `tupleNumber.version`.
     * @since 3.0
     */
    val version: Long
        get() = tupleNumber.version

    /**
     * Tests if the `Tuple` is a tombstone, so a deleted state, actually similar to `tupleNumber.isTombstone`.
     * @since 3.0
     * @see TupleNumber.isTombstone
     */
    @get:JvmName("isTombstone")
    val isTombstone: Boolean
        get() = tupleNumber.isTombstone

    /**
     * The next-version at which this tuple was superseded, `null` indicates the tuple is the current _([Version.HEAD])_ state.
     * @since 3.0
     */
    var nextVersion: Long
        get() = membersBook[StandardMembers.NextVersion.name] as Long? ?: Version.HEAD.number
        set(value) {
            val members = this.membersBook
            if (members is HeapBook) {
                members.put(StandardMembers.NextVersion.name, value)
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
            if (raw is Long) {
                TODO("Use the global book number as feature-number, combine with storage-number from tuple, and with admin-catalog, book-collection, version is always HEAD, books are immutable and can not be versioned")
            }
            _globalBookTn = globalBookTn
            return globalBookTn
        }

    private var _id: Id? = null

    /**
     * The identifier of the feature.
     *
     * The [Id] instance will be cached, and is only generated ones in the lifetime of a [Tuple].
     * @since 3.0
     * @throws NakshaException with error [ILLEGAL_STATE] if the feature-number is negative, but no textual identifier is contained.
     */
    val id: Id
        get() {
            var id: Id? = _id
            if (id != null) return id
            val text = membersBook[StandardMembers.Id.name] as String?
            val number = tupleNumber.featureNumber
            if (text == null) {
                if (number < 0) {
                    throw illegalState("The tuple does not encode the textual identifier, but has a negative feature-number")
                }
                id = Id(number)
                _id = id
                return id
            }
            // The database must restore the 64-bit feature-number even for catalogs and collections.
            id = Id(text, number, skipVerification = true)
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
            member.name == StandardMembers.FeatureVersion.name ||
            member.name == StandardMembers.Action.name
        if (isVirtual) {
            val value = rawMember(member) ?: return false
            return dataType == null || dataType.isInstance(value)
        }
        val index = membersBook.indexOfName(member.name)
        return index >= 0 && (dataType == null || dataType.isInstance(membersBook[index]))
    }

    /**
     * Resolve a physically stored member or one of the virtual tuple-number members.
     * Virtual members are deliberately not duplicated in [membersBook].
     */
    private fun rawMember(member: Member): Any? = when (member.name) {
        StandardMembers.FeatureNumber.name -> tupleNumber.featureNumber
        StandardMembers.FeatureVersion.name -> tupleNumber.version
        StandardMembers.Action.name -> tupleNumber.action
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
            is Byte -> raw.toLong()
            is Short -> raw.toLong()
            is Int -> raw.toLong()
            is Long -> raw
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
    fun getTagMap(member: Member): TagMap? {
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
     * Decode this [Tuple] into an object.
     * @param globalBook The global book to use to decode the [Tuple]. Need to be loaded from the storage, if [globalBookTn] is not `null`.
     * @return the decoded [AnyObject].
     * @since 3.0
     * @throws NakshaException if any error occurs.
     */
    fun decodeObject(globalBook: IBook?): AnyObject {
        val rawBytes = if (isGzipped(featureBytes)) gzipInflate(featureBytes) else featureBytes
        val decoder = JbDecoder2(globalBook, membersBook)
        decoder.mapBytes(rawBytes)
        return decoder.toAnyObject()
    }

    /**
     * Decode this [Tuple] into a [NakshaCatalog].
     * @return the decoded [NakshaCatalog].
     * @since 3.0
     * @throws NakshaException if any error occurs.
     */
    fun decodeCatalog(): NakshaCatalog = decodeObject(null).proxy(NakshaCatalog::class)

    /**
     * Decode this [Tuple] into a [NakshaCollection].
     * @return the decoded [NakshaCollection].
     * @since 3.0
     * @throws NakshaException if any error occurs.
     */
    fun decodeCollection(): NakshaCollection = decodeObject(null).proxy(NakshaCollection::class)

    /**
     * Decode this [Tuple] into a [NakshaTx].
     * @return the decoded [NakshaTx].
     * @since 3.0
     * @throws NakshaException if any error occurs.
     */
    fun decodeTransaction(): NakshaTx = decodeObject(null).proxy(NakshaTx::class)

    // TODO: decodeBook(): ?
    //       Add `val globalBookTn: TupleNumber?` and decode this, if there is a global book needed for decoding.
    //       We need to have some helper at the session that returns books like `getBook(tn: TupleNumber)`

    /**
     * Decode this [Tuple] into a [NakshaFeature].
     * @param globalBook The global book to use to decode the [Tuple]. Need to be loaded from the storage, if [globalBookTn] is not `null`.
     * @return the decoded [NakshaFeature].
     * @since 3.0
     * @throws NakshaException if any error occurs.
     */
    fun decodeFeature(globalBook: IBook?): NakshaFeature = decodeObject(globalBook).proxy(NakshaFeature::class)

    /**
     * Decode this [Tuple] into a given proxy.
     * @param klass the type to proxy.
     * @param globalBook The global book to use to decode the [Tuple]. Need to be loaded from the storage, if [globalBookTn] is not `null`.
     * @return the tuple decoded as given proxy.
     * @since 3.0
     * @throws NakshaException if any error occurs.
     */
    @JvmOverloads
    fun <T: AnyObject> decodeAs(klass: KClass<T>, globalBook: IBook? = null ): T = decodeObject(globalBook).proxy(klass)
}

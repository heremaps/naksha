package naksha.model

import naksha.base.Int64
import naksha.base.PlatformDataView
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_get_int32
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_get_int64
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_get_int8
import naksha.model.BinaryUtil.BinaryUtil_C.readTimestamp
import naksha.model.BinaryUtil.BinaryUtil_C.readTupleNumber
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import kotlin.jvm.JvmField

/**
 * A reader that must be placed to the start of the metadata encoding in a binary, and that can decode the metadata at runtime.
 *
 * This is used to read a binary as [IMetadata].
 * @since 3.0.0
 */
class MetadataBinary(
    /**
     * The view to map.
     * @since 3.0.0
     */
    @JvmField
    val view: PlatformDataView,

    /**
     * The offset in the view where the metadata binary encoding starts.
     * @since 3.0.0
     */
    offset: Int
) : IMetadata {
    private var _nextTxnOffset = -1
    private var _prevTupleNumberOffset = -1
    private var _baseTupleNumberOffset = -1
    private var _createdAtOffset = -1
    private var _authorTsOffset = -1
    private var _updateAtOffset = -1
    private var _tupleNumber = TupleNumber.HEAD
    private var _version: Version = Version.HEAD
    private var _nextVersion: Version? = Version.HEAD
    private var _prevTupleNumber: TupleNumber? = TupleNumber.HEAD
    private var _baseTupleNumber: TupleNumber? = TupleNumber.HEAD
    private var _guid: Guid? = null
    private var _originGuid: Guid? = null
    private var _targetGuid: Guid? = null

    private var _id: String? = null
    private var _appId: String? = null
    private var _author: String? = null
    private var _origin: String? = null
    private var _target: String? = null
    private var _end = -1
    private fun clearCache() {
        _tupleNumber = TupleNumber.HEAD
        _updateAtOffset = -1
        _version = Version.HEAD
        _nextVersion = Version.HEAD
        _prevTupleNumber = TupleNumber.HEAD
        _baseTupleNumber = TupleNumber.HEAD
        _guid = null
        _id = null
        _appId = null
        _author = null
        _origin = null
        _originGuid = null
        _target = null
        _targetGuid = null
        _end = -1
    }
    private fun updateCache(): Boolean {
        if (_updateAtOffset < 0) {
            val flags = this.flags
            var offset = this.offset
            if (flags.hasNextVersion()) {
                _nextTxnOffset = offset
                offset += 8
            } else _nextTxnOffset = -1

            if (flags.hasPrevTupleNumber()) {
                _prevTupleNumberOffset = offset
                offset += 12
            } else _prevTupleNumberOffset = -1

            if (flags.hasBaseTupleNumber()) {
                _baseTupleNumberOffset = offset
                offset += 12
            } else _baseTupleNumberOffset = -1

            if (flags.hasCreatedAt()) {
                _createdAtOffset = offset
                offset += 6
            } else _createdAtOffset = -1

            if (flags.hasAuthorTs()) {
                _authorTsOffset = offset
                offset += 6
            } else _authorTsOffset = -1
            _updateAtOffset = offset
            val cstring = CStringReader(view, _updateAtOffset + 18)
            _id = cstring.readNext("id")
            _appId = cstring.readNext("appId")
            _author = cstring.readNext("author")
            _origin = cstring.readNext("origin")
            _target = cstring.readNext("target")
            _end = cstring.offset
            return true
        }
        return false
    }

    /**
     * The offset in the view where the metadata binary encoding starts.
     * @since 3.0.0
     */
    var offset: Int = offset
        set(value) {
            if (field != value) clearCache()
            field = value
        }

    override val storageNumber: Int64
        get() = dataview_get_int64(view, offset)

    override val mapNumber: Int
        get() = dataview_get_int32(view, offset + 8)

    override val collectionNumber: Int
        get() = dataview_get_int32(view, offset + 12)

    // Note, the lowest byte of the txn encodes the partition-number.
    override val txn: Int64
        get() = dataview_get_int64(view, offset + 16) shr 8

    // Note, the lowest byte of the txn encodes the partition-number.
    override val partitionNumber: Int
        get() = dataview_get_int8(view, offset + 16 + 7).toInt() and 255

    override val version: Version
        get() {
            updateCache()
            var version = this._version
            if (version === Version.HEAD) {
                version = Version(txn)
                this._version = version
            }
            return version
        }

    override val uid: Int
        get() = dataview_get_int32(view, offset + 24)

    override val tupleNumber: TupleNumber
        get() {
            updateCache()
            var tupleNumber = this._tupleNumber
            if (tupleNumber == TupleNumber.HEAD) {
                tupleNumber = TupleNumber(storageNumber, mapNumber, collectionNumber, partitionNumber, version, uid)
                this._tupleNumber = tupleNumber
            }
            return tupleNumber
        }

    override val flags: Flags
        get() = dataview_get_int32(view, offset + 28)

    override val txnNext: Int64?
        get() {
            updateCache()
            val nextTxnOffset = this._nextTxnOffset
            return if (nextTxnOffset >= 0) dataview_get_int64(view, nextTxnOffset) else null
        }

    override val nextVersion: Version?
        get() {
            updateCache()
            var version = this._nextVersion
            if (version === Version.HEAD) {
                val txn = txnNext
                version = if (txn != null) Version(txn) else null
                this._nextVersion = version
            }
            return version
        }

    override val prevTupleNumber: TupleNumber?
        get() {
            updateCache()
            var value = this._prevTupleNumber
            if (value === TupleNumber.HEAD) {
                val tn = this.tupleNumber
                value = readTupleNumber(view, _prevTupleNumberOffset, tn.storageNumber, tn.mapNumber, tn.collectionNumber)
                this._prevTupleNumber = value
            }
            return value
        }

    override val baseTupleNumber: TupleNumber?
        get() {
            updateCache()
            var value = this._baseTupleNumber
            if (value === TupleNumber.HEAD) {
                val tn = this.tupleNumber
                value = readTupleNumber(view, _baseTupleNumberOffset, tn.storageNumber, tn.mapNumber, tn.collectionNumber)
                this._baseTupleNumber = value
            }
            return value
        }

    override val createdAt: Int64?
        get() = if (flags.hasCreatedAt() && _createdAtOffset >= 0) readTimestamp(view, _createdAtOffset) else null

    override val authorTs: Int64?
        get() = if (flags.hasAuthorTs() && _authorTsOffset >= 0) readTimestamp(view, _authorTsOffset) else null

    override val updatedAt: Int64
        get() = readTimestamp(view, _updateAtOffset)

    override val changeCount: Int
        get() = dataview_get_int32(view, _updateAtOffset + 6)

    override val hash: Int
        get() = dataview_get_int32(view, _updateAtOffset + 10)

    override val hereTile: Int
        get() = dataview_get_int32(view, _updateAtOffset + 14)

    override val id: String
        get() = _id ?: throw NakshaException(ILLEGAL_STATE, "The id is null")

    override val appId: String
        get() = _appId ?: throw NakshaException(ILLEGAL_STATE, "The appId is null")

    override val author: String?
        get() = _author

    override val origin: String?
        get() = _origin

    override val originGuid: Guid?
        get() {
            var guid = _originGuid
            if (guid == null) {
                val origin = this.origin ?: return null
                guid = Guid.fromString(origin)
                _originGuid = guid
            }
            return guid
        }

    override val target: String?
        get() = _target

    override val targetGuid: Guid?
        get() {
            var guid = _targetGuid
            if (guid == null) {
                val target = this.target ?: return null
                guid = Guid.fromString(target)
                _targetGuid = guid
            }
            return guid
        }

    override val guid: Guid
        get() {
            var guid = _guid
            if (guid == null) {
                guid = Guid(id, tupleNumber)
                this._guid = guid
            }
            return guid
        }

    /**
     * Create a heap copy of this binary.
     * @return the heap copy of this binary.
     * @since 3.0.0
     * @see [Metadata.fromOther]
     */
    fun toMetadata(): Metadata = Metadata.fromOther(this)
}
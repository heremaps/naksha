@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.*
import naksha.geo.SpBoundingBox
import naksha.geo.SpGeometry
import naksha.geo.SpPoint
import naksha.model.*
import kotlin.js.JsExport
import kotlin.jvm.JvmOverloads

/**
 * A transaction feature as stored in Naksha storages.
 *
 * This is a bit special, it requires that [id] is the stringified [version].
 *
 * @since 3.0
 */
@JsExport
open class NakshaTx : NakshaFeature() {

    override fun defaultFeatureType(): String = "naksha.Tx"
    override fun withId(value: String): NakshaTx = super.withId(value) as NakshaTx
    override fun withFeatureNumber(value: Int64): NakshaTx = super.withFeatureNumber(value) as NakshaTx
    override fun withType(value: String): NakshaTx = super.withType(value) as NakshaTx
    override fun withFeatureType(value: String): NakshaTx = super.withFeatureType(value) as NakshaTx
    override fun withBbox(value: SpBoundingBox?): NakshaTx = super.withBbox(value) as NakshaTx
    override fun withGeometry(value: SpGeometry?): NakshaTx = super.withGeometry(value) as NakshaTx
    override fun withReferencePoint(value: SpPoint?): NakshaTx = super.withReferencePoint(value) as NakshaTx
    override fun withProperties(value: NakshaProperties): NakshaTx = super.withProperties(value) as NakshaTx
    override fun withAttachment(value: ByteArray?): NakshaTx = super.withAttachment(value) as NakshaTx
    override fun withMomType(value: String?): NakshaTx = super.withMomType(value) as NakshaTx

    companion object NakshaTransaction_C {
        private val INT_0 = NotNullProperty<NakshaTx, Int>(Int::class, init = { _, _ -> 0 })
        private val MAPS = NotNullProperty<NakshaTx, NakshaTxMapById>(NakshaTxMapById::class)
        private val INT64_NULL = NotNullProperty<NakshaTx, Int64>(Int64::class)
        private val TIME = NotNullProperty<NakshaTx, Int64>(Int64::class) { _, _ -> Platform.currentMillis() }
    }

    /**
     * Sets [id], [version], [txn], and [time] in a synchronized manner.
     * @param epoch the timestamp of the transaction.
     * @param seq the sequence within the day.
     * @since 3.0
     */
    @JvmOverloads
    fun setEpoch(epoch: Timestamp, seq: Int64 = Int64(0)): NakshaTx {
        val version = Version.of(epoch.year, epoch.month, epoch.day, seq)
        setRaw("id", version.toString())
        setRaw("time", epoch.ts)
        return this
    }

    /**
     * Sets [id], [version], [txn], and [time] in a synchronized manner to match the given [version].
     *
     * Set `hour`, `minute`, `second`, and `millis` of the transaction [time] to the current local values, adjusted to [UTC](https://en.wikipedia.org/wiki/Coordinated_Universal_Time).
     * @param version the version, as for example read from a storage atomic.
     * @return this.
     * @since 3.0
     */
    fun setVersion(version: Version): NakshaTx {
        val now = Timestamp.now()
        val epoch = Timestamp.fromDate(
            version.year,
            version.month,
            version.day,
            now.hour,
            now.minute,
            now.second,
            now.nanos
        )
        setEpoch(epoch, version.seq)
        return this
    }

    /**
     * Creates a new transaction from the [current time](https://en.wikipedia.org/wiki/Unix_time).
     * @param seq the sequence with which to initialize the sequence, defaults to `0`.
     * @return the [NakshaTx].
     * @since 3.0
     */
    @JvmOverloads
    fun setNow(seq: Int64? = null): NakshaTx {
        setEpoch(Timestamp.now(), seq ?: Int64(0))
        return this
    }

    /**
     * The feature-id of the transaction.
     *
     * ### Warning
     * The feature-id of a transaction **must be** the stringified [version].
     */
    override var id: String
        get() = getAs("id", String::class) ?: throw illegalState("The property 'id' must be a valid string")
        set(value) {
            val txn = Int64(value.toLong())
            setVersion(Version(txn))
        }

    /**
     * The [unix epoch](https://en.wikipedia.org/wiki/Unix_time) time in milliseconds of when the transaction has started.
     * @since 3.0
     * @see [Metadata.updatedAt]
     * @see [Metadata.createdAt]
     * @see [Metadata.authorTs]
     */
    val time by TIME

    private var _epoch: Timestamp? = null
    private var _epochTime: Int64? = null

    /**
     * The [time] as [Timestamp].
     * @since 3.0
     */
    val epoch: Timestamp
        get() {
            val time = this.time
            var _epoch = this._epoch
            val _epochTime = this._epochTime
            if (_epoch != null && _epochTime === time) return _epoch
            _epoch = Timestamp.fromMillis(time)
            this._epochTime = time
            this._epoch = _epoch
            return _epoch
        }

    private var _versionId: String? = null
    private var _version: Version? = null

    /**
     * Returns the transaction number as [Version] by parsing the [id].
     *
     * - Throws [NakshaError.ILLEGAL_ARGUMENT], if the given string is of an invalid format.
     * @return the transaction number as [Version].
     * @since 3.0
     */
    val version: Version
        get() {
            val id = this.id
            val _versionId = this._versionId
            var _version = this._version
            if (_version != null && _versionId === id) return _version
            _version = Version.fromString(id)
            this._versionId = id
            this._version = _version
            return _version
        }

    /**
     * The transaction number, basically just `version.txn`.
     * @since 3.0
     */
    val txn: Int64
        get() = version.txn

    /**
     * Number of features modified in the transaction - total number of features from all touched collections.
     *
     * Note, the value is updated by the sequencer, and up until this was done, the number is just an estimation.
     * @since 3.0
     */
    var featuresModified: Int by INT_0

    /**
     * Total number of bytes of all rows being in the transaction.
     *
     * Note, the value is updated by the sequencer, and up until this was done, the number is just an estimation.
     * @since 3.0
     */
    var featuresBytes: Int by INT_0

    /**
     * The sequence number of the transaction, what is a sequential number starting with 1 for the first transaction, it has no holes and is generated by a sequencer job. Therefore, transactions that have not been sequenced yet have no [sequence number][seqNumber] (_null_), nor a [sequence timestamp][seqTs] (_null_).
     * @since 3.0
     */
    var seqNumber by INT64_NULL

    /**
     * The sequencing time in epoch milliseconds, or _null_, when no sequencing has yet been done of this transaction.
     * @since 3.0
     */
    var seqTs by INT64_NULL

    /**
     * The maps that were modified as part of this transaction.
     * @since 3.0
     */
    val maps by MAPS

    /**
     * Returns the [map information][NakshaTxMap], if none exists yet, create them and return them.
     * @param id the map-id.
     * @param number the map-number.
     * @param action the action done to the map.
     * @return the transaction map information.
     * @since 3.0
     */
    fun useMap(id: String, number: Int, action: Action): NakshaTxMap {
        val existing = maps[id]
        if (existing != null) {
            existing.action = action
            return existing
        }
        val mapInfo = NakshaTxMap(id, number, action)
        maps[id] = mapInfo
        return mapInfo
    }
}
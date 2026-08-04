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
 * This is a bit special, it requires that [id] is the stringified [asVersion] _(so a positive 53-bit integer)_ and offers helper methods to convert the `id` back into the version. Normally the transaction is only created by the storage.
 *
 * Be aware that the transaction itself does have a version too, but it differs from the `id`, because the `id` does have the lower two bit always set, while the version encodes [CREATE][Action.CREATE]. When the transaction is later modified, the [asVersion] of the transaction, and the [asVersion] that the transaction represents, will diverge further.
 *
 * Therefore: The feature-number in the [TupleNumber] of the transaction is the same as the `id`, but the version in the [TupleNumber] is **NOT** the version that the transaction feature represents, this is the [id]! Therefore, if you
 *
 * @since 3.0
 */
@JsExport
open class NakshaTx : NakshaFeature() {

    override fun withId(value: Id?): NakshaTx = super.withId(value) as NakshaTx // Note: We override the `id` getter/setter!
    override fun withType(value: String?): NakshaTx = super.withType(value) as NakshaTx
    override fun withFeatureType(value: FeatureType?): NakshaTx = super.withFeatureType(value) as NakshaTx
    override fun featureTypeDefaultValue(): FeatureType = FeatureType.TRANSACTION
    override fun withBbox(value: SpBoundingBox?): NakshaTx = super.withBbox(value) as NakshaTx
    override fun withGeometry(value: SpGeometry?): NakshaTx = super.withGeometry(value) as NakshaTx
    override fun withReferencePoint(value: SpPoint?): NakshaTx = super.withReferencePoint(value) as NakshaTx
    override fun withProperties(value: NakshaProperties): NakshaTx = super.withProperties(value) as NakshaTx
    override fun withMomType(value: String?): NakshaTx = super.withMomType(value) as NakshaTx

    companion object NakshaTransaction_C {
        private const val UNINITIALIZED_MSG = "Invoke 'init' first to initialized NakshaTx"
        private val INT_0 = NotNullProperty<NakshaTx, Int>(Int::class, init = { _, _ -> 0 })
        private val CATALOGS = NotNullProperty<NakshaTx, NakshaTxCatalogByIdText>(NakshaTxCatalogByIdText::class)
        private val INT64_NULL = NotNullProperty<NakshaTx, Int64>(Int64::class)
        private val TIME = NotNullProperty<NakshaTx, Int64>(Int64::class) { _, _ -> Base.currentMillis() }
    }

    /**
     * Sets [id] and [time] to match the given version.
     *
     * If a dated version is given, the method uses `year`, `month`, and `day` from the given version and sets `hour`, `minute`, `second`, and `millis` of the transaction [time] to the current local values, adjusted to [UTC](https://en.wikipedia.org/wiki/Coordinated_Universal_Time); otherwise the [time] is just the local time as [UTC](https://en.wikipedia.org/wiki/Coordinated_Universal_Time).
     * @param databaseId the [Id] of the database in which the version is stored.
     * @param ver the version to initialize the transaction for.
     * @return this.
     * @since 3.0
     * @throws NakshaException with error being [NakshaError.ILLEGAL_STATE], when this method is called a second time.
     */
    fun init(databaseId: Id, ver: Version): NakshaTx {
        if (getRaw("id") is String) throw illegalState("A transaction can only be initialized ones")
        val now = Timestamp.now()
        val epoch = if (ver.isDated()) Timestamp.fromDate(
            ver.year,
            ver.month,
            ver.day,
            now.hour,
            now.minute,
            now.second,
            now.nanos
        ) else now
        // Note: The feature-number of the transaction MUST have the lower two bit set.
        //       11b encodes action=VERSION, other values are CREATE, UPDATE and DELETE.
        //       As this feature represents the version, it must have action set to VERSION.
        val featureNumber = ver.number or 3L
        val id = Id(featureNumber)
        setRaw("databaseId", databaseId)
        setRaw("id", id)
        setRaw("time", epoch.ts)
        _asVersion = if (ver.number == featureNumber) ver else null
        return this
    }

    /**
     * The `id` of the transaction, which is the version for which it was created and that it represents.
     * @throws NakshaException with error [NakshaError.UNSUPPORTED_OPERATION] when set to a value and with [NakshaError.UNINITIALIZED], when [init] has not yet been invoked.
     * @see init
     * @see asVersion
     */
    override var id: Id
        get() = getId("id", true) ?: throw uninitialized(UNINITIALIZED_MSG)
        set(_) {
            throw unsupportedOp("Use 'init' to initialize NakshaTx, manual modification of 'id' is not supported")
        }

    /**
     * The [unix epoch](https://en.wikipedia.org/wiki/Unix_time) time in milliseconds of when the transaction has started.
     * @since 3.0
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

    private var _asVersion: Version? = null

    /**
     * Converts the [id] into [Version].
     * @return the [id] converted into [Version].
     * @since 3.0
     * @throws NakshaException with error [NakshaError.UNINITIALIZED], when [init] has not yet been invoked, this means there is no valid [id].
     */
    val asVersion: Version
        get() {
            var v = _asVersion
            if (v != null) return v
            v = Version(id.number)
            _asVersion = v
            return v
        }

    /**
     * The `id` of the [database][NakshaDatabase] in which the transaction has happened and that was modified.
     * @since 3.0
     */
    val databaseId: Id
        get() = getId("databaseId", true) ?: throw uninitialized(UNINITIALIZED_MSG)

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
     * This number will provide an impression about how much data has to be loaded, when the full features being part of this transaction have to be read from the storage. It is calculated about the binary encoded [Tuple].
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
     * The catalogs that were modified as part of this transaction.
     * @since 3.0
     */
    val catalogs by CATALOGS

    /**
     * Returns the [catalog information][NakshaTxCatalog], if none exists yet, create them and return them.
     * @param id the `id` of the catalog.
     * @param action the action done to the map, `null` if only children were modified.
     * @return the transaction map information.
     * @since 3.0
     */
    @JvmOverloads
    fun useCatalog(id: Id, action: Action? = null): NakshaTxCatalog {
        val existing = catalogs[id.text]
        if (existing != null) {
            if (action != null) existing.action = action
            return existing
        }
        val catalogInfo = NakshaTxCatalog(id, action)
        catalogs[id.text] = catalogInfo
        return catalogInfo
    }
}
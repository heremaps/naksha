@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.Int64
import naksha.base.NotNullProperty
import naksha.base.Platform
import naksha.geo.SpBoundingBox
import naksha.geo.SpGeometry
import naksha.geo.SpPoint
import naksha.model.*
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A transaction feature as stored in Naksha storages.
 *
 * The [transaction-number][txn] is reserved for the client, no matter how long it takes to process the transaction.
 *
 * The [versionField] can be used by clients to create new tuples, and then use [ISession.execute] to persist the tuples. This is more complicated than simply using [Naksha features][naksha.model.objects.NakshaFeature] in the [ISession.execute], but allows much more fine-grained control, specifically it allows to order the execution using the `uid`. It allows to build own [Tuple] with dedicated [TupleNumber], by combining the [IStorage.number], [IMap.number], [ICollection.number], [Version], and a client side generated `uid`.
 *
 * @since 3.0.0
 */
@JsExport
open class NakshaTransaction() : NakshaFeature() {

    /**
     * Create a new transaction with the given transaction number.
     * @param txn the transaction number.
     * @param time the unix epoch time in milliseconds of when the transaction started.
     */
    @JsName("of")
    constructor(txn: Int64, time: Int64 = Platform.currentMillis()) : this() {
        this.txn = txn
        this.time = time
    }

    companion object NakshaTransaction_C {
        private val INT_0 = NotNullProperty<NakshaTransaction, Int>(Int::class, init = { _, _ -> 0 })
        private val COLLECTIONS = NotNullProperty<NakshaTransaction, NakshaTxCollectionInfoMap>(NakshaTxCollectionInfoMap::class)
        private val INT64_NULL = NotNullProperty<NakshaTransaction, Int64>(Int64::class)
        private val TIME = NotNullProperty<NakshaTransaction, Int64>(Int64::class) { _, _ -> Platform.currentMillis() }
    }

    override fun defaultFeatureType(): String = "naksha.Tx"
    override fun withId(value: String): NakshaTransaction = super.withId(value) as NakshaTransaction
    override fun withType(value: String): NakshaTransaction = super.withType(value) as NakshaTransaction
    override fun withFeatureType(value: String): NakshaTransaction = super.withFeatureType(value) as NakshaTransaction
    override fun withBbox(value: SpBoundingBox?): NakshaTransaction = super.withBbox(value) as NakshaTransaction
    override fun withGeometry(value: SpGeometry?): NakshaTransaction = super.withGeometry(value) as NakshaTransaction
    override fun withReferencePoint(value: SpPoint?): NakshaTransaction = super.withReferencePoint(value) as NakshaTransaction
    override fun withProperties(value: NakshaProperties): NakshaTransaction = super.withProperties(value) as NakshaTransaction
    override fun withAttachment(value: ByteArray?): NakshaTransaction = super.withAttachment(value) as NakshaTransaction
    override fun withMomType(value: String): NakshaTransaction = super.withMomType(value) as NakshaTransaction

    override var id: String
        get() {
            val id = getRaw("id")
            if (id is String) return id
            setRaw("id", "0")
            setRaw("txn", Int64(0))
            return "0"
        }
        set(value) {
            setRaw("txn", Int64(value.toLong()))
            setRaw("id", value)
        }

    /**
     * The transaction number.
     * @since 3.0.0
     */
    var txn: Int64
        get() = getOrCreate<Int64, String, NakshaTransaction>("txn", Int64::class) { self, _ ->
            self.id = "0"
            Int64(0)
        }
        set(value) {
            setRaw("id", value.toString())
            setRaw("txn", value)
        }

    /**
     * @see [txn]
     */
    open fun withTxn(value: Int64): NakshaTransaction {
        txn = value
        return this
    }

    /**
     * The [unix epoch](https://en.wikipedia.org/wiki/Unix_time) time in milliseconds of when the transaction has started.
     * @since 3.0.0
     */
    var time by TIME

    // Note: We do not want the version to be stored in the JSON object, because this
    //       is just a wrapper class for txn!
    private var versionField: Version? = null

    /**
     * Returns the transaction number as [Version].
     * @return the transaction number as [Version].
     * @since 3.0.0
     */
    var version: Version
        get() {
            val txn = this.txn
            var v = versionField
            if (v == null || v.txn != txn) {
                v = Version(txn)
                versionField = v
            }
            return v
        }
        set(value) {
            txn = value.txn
            versionField = value
        }

    /**
     * @see [version]
     */
    open fun withVersion(value: Version): NakshaTransaction {
        version = value
        return this
    }

    /**
     * The next `uid`.
     * @since 3.0.0
     */
    var uid by INT_0

    /**
     * Returns the current `uid` value, then increments it.
     *
     * The method is not thread-safe, but allows to draw new `uid` values. The smallest value is `0`, the biggest `-1`, because the value is treated as unsigned integer.
     * @since 3.0.0
     */
    fun getAndAddUid(): Int {
        val uid = this.uid
        this.uid += 1
        return uid
    }

    /**
     * Number of features modified in the transaction - total number of features from all touched collections.
     *
     * Note, the value is updated by the sequencer, and up until this was done, the number is just an estimation.
     * @since 3.0.0
     */
    var featuresModified: Int by INT_0

    /**
     * Total number of bytes of all rows being in the transaction.
     *
     * Note, the value is updated by the sequencer, and up until this was done, the number is just an estimation.
     * @since 3.0.0
     */
    var featuresBytes: Int by INT_0

    /**
     * The sequence number of the transaction, what is a sequential number starting with 1 for the first transaction, it has no holes and is generated by a sequencer job. Therefore, transactions that have not been sequenced yet have no [sequence number][seqNumber] (_null_), nor a [sequence timestamp][seqTs] (_null_).
     * @since 3.0.0
     */
    var seqNumber by INT64_NULL

    /**
     * The sequencing time in epoch milliseconds, or _null_, when no sequencing has yet been done of this transaction.
     * @since 3.0.0
     */
    var seqTs by INT64_NULL

    /**
     * A map of the collections that have been modified as part of this transaction.
     * @since 3.0.0
     */
    var collections by COLLECTIONS

    /**
     * Returns the collection-info of the given collection, if it does not yet exist, creates a new empty one.
     * @param collectionId the collection identifier.
     * @return the transaction collection information.
     * @since 3.0.0
     */
    fun useTxCollectionInfo(collectionId: String): NakshaTxCollectionInfo {
        var info = collections[collectionId]
        if (info == null) {
            info = NakshaTxCollectionInfo()
            collections[collectionId] = info
        }
        return info
    }

    /**
     * Add the given collection info, if it exists already, otherwise a new entry is created, and the given values are added.
     * @param info the collection info to add.
     * @since 3.0.0
     */
    fun addTxCollectionInfo(info: NakshaTxCollectionInfo) {
        useTxCollectionInfo(info.collectionId).addValues(info)
    }
}
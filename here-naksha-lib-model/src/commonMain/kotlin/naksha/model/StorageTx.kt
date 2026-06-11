@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.jbon.IDictReader
import naksha.jbon.HeapBook
import naksha.model.objects.*
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A wrapper for an ongoing Naksha transaction, used by [write session's][IWriteSession].
 *
 * This is a rather low level class, that normally applications should not care about, and which is not exposed by the session. It implements the _standard_ way of [Tuple] encoding and decoding. It helps storage implementors, and test implementations to generate [Tuple] without a storage.
 *
 * This class works without any real [storage][IStorage], however, storages can extend this class, and override certain behaviors with own better implementations, adjusted to the storage.
 *
 * @since 3.0
 * @see [Naksha.decodeTuple]
 */
@JsExport
open class StorageTx private constructor(
    /**
     * The storage instance for which this transaction is done. Does not have to be supplied.
     * @since 3.0
     * @see [IStorage]
     */
    val storage: IStorage? = null,

    /**
     * The storage-number of the storage for which this transaction is done.
     * @since 3.0
     * @see [NakshaStorage.number]
     */
    val storageNumber: Int64,

    /**
     * The unique version of the transaction. This value **should be** unique to this transaction.
     * @since 3.0
     * @see [Version.auto]
     * @see [Version.manual]
     * @see [Version.now]
     */
    val version: Version,

    /**
     * The application-id of the application performing the modifications.
     * @since 3.0
     */
    val appId: String,

    /**
     * The author _(user)_ that performs the modifications; if any.
     *
     * If `null`, then the change is done by an application and the author and authorTs fields in [Tuple.members] are not modified _(they stay what they are right now)_.
     * @since 3.0
     */
    val author: String?,

    /**
     * The dictionary reader to be used to encode and decode features.
     * @since 3.0
     */
    val dictReader: IDictReader?,

    /**
     * The session to which this transaction is attached.
     * @since 3.0
     */
    val session: ISession,
) {

    @JsName("storageTxWithStorageNumber")
    constructor(
        storageNumber: Int64,
        version: Version,
        appId: String,
        author: String?,
        dictReader: IDictReader?,
        session: ISession,
    ): this(null, storageNumber, version, appId, author, dictReader, session)

    @JsName("storageTxWithStorage")
    constructor(
        storage: IStorage,
        version: Version,
        appId: String,
        author: String?,
        dictReader: IDictReader?,
        session: ISession,
    ): this(storage, storage.number, version, appId, author, dictReader, session)

    /**
     * The statistical transaction information, updated while this class is being used, should eventually be writted into the transaction-log of the storage.
     * @since 3.0
     */
    open val transaction: NakshaTx = NakshaTx().setVersion(version)

    /**
     * The `updated_at` value being used for all [Tuple] created, basically just reads `transaction.time`.
     * @since 3.0
     */
    open val updatedAt: Int64
        get() = transaction.time

    /**
     * Builds the [Tuple] for the given write action.
     * @param map the map in which the feature is being persisted.
     * @param collection the collection in which the feature is being persisted.
     * @param feature the feature.
     * @param action the [action][Action] being performed.
     * @return the encoded [Tuple].
     */
    private fun buildTuple(
        map: NakshaMap,
        collection: NakshaCollection,
        feature: NakshaFeature,
        action: Action,
    ): Tuple {
        val membersBook = HeapBook()
        val globalBook = dictReader?.getEncodingDictionary(feature)
        val featureBytes = Naksha.encodeFeature(feature, collection, session, membersBook, globalBook)

        val actionBits = Int64(action.intValue.toLong())
        val featureNumber = feature.featureNumber
        val versionVal = version.txn and Int64(-4L) or actionBits
        val nextVersion: Int64 = if (map.id == Naksha.ADMIN_MAP && collection.id == Naksha.TRANSACTIONS_COL) versionVal else Int64(-1L)
        return Tuple(
            storageNumber = storageNumber,
            mapNumber = map.number,
            collectionNumber = collection.number,
            featureNumber = featureNumber,
            version = Version(versionVal),
            nextVersion = nextVersion,
            members = membersBook,
            feature = featureBytes
        )
    }

    /**
     * Convert the given [feature][NakshaFeature] into a [Tuple], when the feature was created.
     *
     * ### Note
     * This method can be used for `upsert` as well, just that on-conflict the following values have to be updated from the already existing feature:
     * - `created_at` - should be `created_at` of the existing version.
     * - `cc` - _(change-count)_ should be set to the existing value + 1
     * - `author` - if the previous `author` is not the same as the current, then set to the current author _(author changed)_, otherwise set it to the previous one _(unchanged)_. Note: if either author is `null`, they are considered different.
     * - `author_ts` - if the author changed, set to `null` _(same as `updatedAt`)_, otherwise set to the previous value.
     * - `flags` - update `authorTs` flag accordingly; always set the `createdAt` flag; change the `action` to `UPDATED`.
     *
     * We need to beware, that in an `UPSERT` operation the [Tuple] changes, when we eventually perform the `UPDATE`, rather than the planned `INSERT`. So, we need to return the modified values in this case, therefore an `UPSERT` is a bit more complicated than an `INSERT` or `UPDATED`, but still we do not need to transfer all data forth and back, we can just create a new updated [Tuple].
     *
     * **Therefore, do not store the [Tuple] in cache before being sure, that it really is persisted!**
     * @param map the map in which the feature is going to be created.
     * @param collection the collection in which the feature is going to be created.
     * @param feature the feature that was created.
     * @return the binary encoding of the [NakshaFeature] as [Tuple].
     */
    open fun created(
        map: NakshaMap,
        collection: NakshaCollection,
        feature: NakshaFeature,
    ): Tuple = buildTuple(map, collection, feature, Action.CREATED)

    /**
     * Convert the given [feature][NakshaFeature] into a [Tuple], when the feature was updated.
     * @param map the map in which the feature is going to be created.
     * @param collection the collection in which the feature is going to be created.
     * @param feature the feature that was created.
     * @return the binary encoding of the [NakshaFeature] as [Tuple].
     */
    open fun updated(
        map: NakshaMap,
        collection: NakshaCollection,
        feature: NakshaFeature,
    ): Tuple = buildTuple(map, collection, feature, Action.UPDATED)

    /**
     * Convert the given [feature][NakshaFeature] into a [Tuple], when the feature was deleted.
     *
     * ### Note
     * There is no difference between purge and delete, except that on a purge, the [Tuple] is not persisted in shadow and/or history, which means that the [TupleNumber] of the purged feature potentially can't be load from storage or cache, so the [Tuple] is not available.
     * @param map the map in which the feature is going to be created.
     * @param collection the collection in which the feature is going to be created.
     * @param feature the feature that was created.
     * @return the binary encoding of the [NakshaFeature] as [Tuple].
     */
    open fun deleted(
        map: NakshaMap,
        collection: NakshaCollection,
        feature: NakshaFeature,
    ): Tuple = buildTuple(map, collection, feature, Action.DELETED)
}

@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.jbon.IDictReader
import naksha.model.Metadata.Metadata_C.calculateHash
import naksha.model.Metadata.Metadata_C.calculateHereTile
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
     * The unique version of the transaction, potentially read from a daily sequence of the database. This value **should be** unique to this transaction!
     * @since 3.0
     * @see [Version.of]
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
     * If `null`, then the change is done by an application and the [author][Metadata.author] and [authorTs][Metadata.authorTs] are not modified _(they stay what they are right now)_.
     * @since 3.0
     */
    val author: String?,

    /**
     * The dictionary reader to be used to encode and decode features.
     * @since 3.0
     */
    val dictReader: IDictReader?,
) {

    @JsName("storageTxWithStorageNumber")
    constructor(
        storageNumber: Int64,
        version: Version,
        appId: String,
        author: String?,
        dictReader: IDictReader?,
    ): this(null, storageNumber, version, appId, author, dictReader)

    @JsName("storageTxWithStorage")
    constructor(
        storage: IStorage,
        version: Version,
        appId: String,
        author: String?,
        dictReader: IDictReader?,
    ): this(storage, storage.number, version, appId, author, dictReader)

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
     * Method to create the new metadata for the given write action on a feature.
     *
     * - Throws [NakshaError.ILLEGAL_ARGUMENT], if the given arguments are not sufficient to generate the new metadata.
     * @param map the map into which to persist the feature.
     * @param collection the collection into which to persist the feature.
     * @param feature the new _(modified)_ state of the feature, for which the metadata should be created.
     * @param action the [action][Action] being performed.
     * @param atomic whether the write should be performed atomically.
     * @return the new metadata that is correct for the new state, based upon the given data.
     * @since 3.0.0
     * @see [StorageTx]
     */
    protected open fun metadataOf(
        map: NakshaMap,
        collection: NakshaCollection,
        feature: NakshaFeature,
        action: Action,
        atomic: Boolean = false
    ): Metadata {
        val flags = getEncodingFlags(feature, collection)
        val xyz = feature.properties.xyz
        val actionBits = Int64(action.intValue.toLong())
        val tn = TupleNumber(storageNumber, map.number, collection.number, feature.featureNumber, Version(version.txn and Int64(-4L) or actionBits))
        // TODO: Handle other operations like rebase!
        val base_tn: TupleNumber? = null
        val isExistingFeature = !(action == Action.CREATED || (action == Action.UPDATED && !atomic))
        if (isExistingFeature && xyz.guid == null) {
            throw illegalArg("$action with atomic=$atomic requires that the feature has a UUID!")
        }
        // Transactions are special: they are partitioned over `next_version` in the HEAD, before being partitioned over `tn` in the year!
        val next_version: Int64? = if (map.id == Naksha.ADMIN_MAP && collection.id == Naksha.TRANSACTIONS_COL) tn.version.txn else null
        val updatedAt: Int64 = this.updatedAt
        val createdAt: Int64? = if (isExistingFeature) xyz.createdAt else null
        val author: String?
        val authorTs: Int64?
        if (xyz.author == null || xyz.author != this.author) {
            // Simulate SQL behavior:
            // If the previous author is null, then it differs from the current author, even when both are null!
            author = this.author
            authorTs = null // authorTs == updatedAt
        } else {
            // If the author stays the same as before, we need to remember the author and timestamp!
            author = xyz.author
            authorTs = xyz.authorTs
        }
        val cv0 = feature.properties.xyz.cv0
        val cv1 = feature.properties.xyz.cv1
        val cv2 = feature.properties.xyz.cv2
        val cv3 = feature.properties.xyz.cv3
        val featureType = if (collection.defaultFeatureType == feature.featureType) null else feature.featureType
        return Metadata(
            tupleNumber = tn,
            flags = flags,
            updatedAt = updatedAt,
            createdAt = createdAt,
            authorTs = authorTs,
            nextVersion = next_version,
            baseTupleNumber = base_tn,
            changeCount = xyz.changeCount + 1,
            hash = calculateHash(feature),
            hereTile = calculateHereTile(feature),
            id = feature.id,
            appId = appId,
            author = author,
            origin = null, // TODO: Fix this, we need to detect foreign features!
            target = null, // TODO: Fix this, we need to detect join operations!
            ft = featureType,
            cv0 = cv0,
            cv1 = cv1,
            cv2 = cv2,
            cv3 = cv3,
            cs0 = feature.properties.xyz.cs0,
            cs1 = feature.properties.xyz.cs1,
            cs2 = feature.properties.xyz.cs2,
            cs3 = feature.properties.xyz.cs3,
        )
    }

    /**
     * Convert the given [feature][NakshaFeature] into a [Tuple], when the feature was created.
     *
     * ### Note
     * This method can be used for `upsert` as well, just that on-conflict the following values have to be updated form the already existing feature:
     * - `created_at` - should be `created_at` of the existing version.
     * - `cc` - _(change-count)_ should be set to the existing value + 1
     * - `author` - if the previous `author` is not the same as the current, then set to the current author _(author changed)_, otherwise set it to the previous one _(unchanged)_.
     * - `author_ts` - if the previous `author` is not the same as the current, set to `null` _(same as `updatedAt`)_, otherwise set to the previous value.
     * - `flags` - if the previous `author` is not the same as the current, set `authorTs` flag, otherwise clear it. Always set the `createdAt` flag _(basically these are only two binary AND/OR's)_, change the `action` to `UPDATED`.
     *
     * About author, beware that comparing `NULL` via `=` to any other value, is by definition always `false` in SQL, so if the previous author, the current author, or both are `null`, we treat this as a changed author!
     *
     * We need to beware, that in an `UPSERT` operation the [Tuple] changes, when we eventually perform the `UPDATE`, rather than the planned `INSERT`. So, we need to return the modified values in this case, therefore an `UPSERT` is a bit more complicated than an `INSERT` or `UPDATED`, but still we do not need to transfer all data forth and back, we can just create a new updated [Tuple].
     *
     * **Therefore, do not store the [Tuple] in cache before being sure, that it really is persisted!**
     * @param map the map in which the feature is going to be created.
     * @param collection the collection in which the feature is going to be created.
     * @param feature the feature that was created.
     * @param attachment the attachment.
     * @return the binary encoding of the [NakshaFeature] as [Tuple].
     */
    open fun created(
        map: NakshaMap,
        collection: NakshaCollection,
        feature: NakshaFeature,
        attachment: ByteArray?
    ): Tuple {
        val metadata = metadataOf(map, collection, feature, Action.CREATED)
        val dictionary = dictReader?.getEncodingDictionary(feature)
        return Tuple(
            meta = metadata,
            feature = Naksha.encodeFeature(feature, metadata.flags, dictionary),
            tags = Naksha.encodeTags(feature.properties.xyz.tags.toTagMap(), dictionary),
            referencePoint = Naksha.encodeGeometry(feature.referencePoint),
            geo = Naksha.encodeGeometry(feature.geometry),
            attachment = attachment,
            complete = true
        )
    }

    /**
     * Convert the given [feature][NakshaFeature] into a [Tuple], when the feature was updated.
     * @param map the map in which the feature is going to be created.
     * @param collection the collection in which the feature is going to be created.
     * @param feature the feature that was created.
     * @param attachment the attachment.
     * @return the binary encoding of the [NakshaFeature] as [Tuple].
     */
    open fun updated(
        map: NakshaMap,
        collection: NakshaCollection,
        feature: NakshaFeature,
        attachment: ByteArray?,
        atomic: Boolean = false,
    ): Tuple {
        val metadata = metadataOf(map, collection, feature, Action.UPDATED, atomic)
        val dictionary = dictReader?.getEncodingDictionary(feature)
        return Tuple(
            meta = metadata,
            feature = Naksha.encodeFeature(feature, metadata.flags, dictionary),
            tags = Naksha.encodeTags(feature.properties.xyz.tags.toTagMap(), dictionary),
            referencePoint = Naksha.encodeGeometry(feature.referencePoint),
            geo = Naksha.encodeGeometry(feature.geometry),
            attachment = attachment,
            complete = true
        )
    }

    /**
     * Convert the given [feature][NakshaFeature] into a [Tuple], when the feature was deleted.
     *
     * ### Note
     * There is no difference between purge and delete, except that on a purge, the [Tuple] is not persisted in shadow and/or history, which means that the [TupleNumber] of the purged feature potentially can't be load from storage or cache, so the [Tuple] is not available.
     * @param map the map in which the feature is going to be created.
     * @param collection the collection in which the feature is going to be created.
     * @param feature the feature that was created.
     * @param attachment the attachment.
     * @return the binary encoding of the [NakshaFeature] as [Tuple].
     */
    open fun deleted(
        map: NakshaMap,
        collection: NakshaCollection,
        feature: NakshaFeature,
        attachment: ByteArray?
    ): Tuple {
        val metadata = metadataOf(map, collection, feature, Action.DELETED)
        val dictionary = dictReader?.getEncodingDictionary(feature)
        return Tuple(
            meta = metadata,
            feature = Naksha.encodeFeature(feature, metadata.flags, dictionary),
            tags = Naksha.encodeTags(feature.properties.xyz.tags.toTagMap(), dictionary),
            referencePoint = Naksha.encodeGeometry(feature.referencePoint),
            geo = Naksha.encodeGeometry(feature.geometry),
            attachment = attachment,
            complete = true
        )
    }

    private fun getEncodingFlags(feature: NakshaFeature, collection: NakshaCollection): Flags =
        storage?.getEncodingFlags(feature, collection) ?: Naksha.DEFAULT_FLAGS
}
@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.jbon.IBook
import naksha.jbon.IDictReader
import naksha.jbon.HeapBook
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
     * The unique version of the transaction. This value **should be** unique to this transaction.
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
     * If `null`, then the change is done by an application and the author and authorTs fields in [Tuple.members] are not modified _(they stay what they are right now)_.
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
     * Method to create the new members dict for the given write action on a feature.
     *
     * - Throws [NakshaError.ILLEGAL_ARGUMENT], if the given arguments are not sufficient to generate the new metadata.
     * @param map the map into which to persist the feature.
     * @param collection the collection into which to persist the feature.
     * @param feature the new _(modified)_ state of the feature, for which the metadata should be created.
     * @param action the [action][Action] being performed.
     * @param atomic whether the write should be performed atomically.
     * @return the new [IBook] with metadata members that is correct for the new state, based upon the given data.
     * @since 3.0.0
     * @see [StorageTx]
     */
    protected open fun buildMembers(
        map: NakshaMap,
        collection: NakshaCollection,
        feature: NakshaFeature,
        action: Action,
        atomic: Boolean = false
    ): IBook {
        val dataEncoding = getDataEncoding(feature, collection)
        val xyz = feature.properties.xyz
        val isExistingFeature = !(action == Action.CREATED || (action == Action.UPDATED && !atomic))
        if (isExistingFeature && xyz.guid == null) {
            throw illegalArg("$action with atomic=$atomic requires that the feature has a UUID!")
        }
        val updatedAt: Int64 = this.updatedAt
        val createdAt: Int64? = if (isExistingFeature) xyz.createdAt else null
        val author: String?
        val authorTs: Int64?
        if (xyz.author == null || xyz.author != this.author) {
            author = this.author
            authorTs = null
        } else {
            author = xyz.author
            authorTs = xyz.authorTs
        }
        val featureType = if (collection.defaultFeatureType == feature.featureType) null else feature.featureType
        val members = HeapBook()
        members.put(StandardMembers.UpdatedAt.name, updatedAt)
        members.put(StandardMembers.CreatedAt.name, createdAt)
        members.put(StandardMembers.AuthorTimestamp.name, authorTs)
        members.put(StandardMembers.Author.name, author)
        members.put(StandardMembers.AppId.name, appId)
        members.put(StandardMembers.DataEncoding.name, dataEncoding.toString())
        members.put(StandardMembers.ChangeCount.name, xyz.changeCount + 1)
        members.put(StandardMembers.Hash.name, calculateHash(feature))
        members.put(StandardMembers.HereTile.name, calculateHereTile(feature))
        members.put(StandardMembers.Id.name, feature.id)
        members.put(StandardMembers.Origin.name, null)
        members.put(StandardMembers.Target.name, null)
        members.put(StandardMembers.FeatureType.name, featureType)
        members.put(StandardMembers.CustomValue0.name, xyz.cv0)
        members.put(StandardMembers.CustomValue1.name, xyz.cv1)
        members.put(StandardMembers.CustomValue2.name, xyz.cv2)
        members.put(StandardMembers.CustomValue3.name, xyz.cv3)
        members.put(StandardMembers.CustomString0.name, xyz.cs0)
        members.put(StandardMembers.CustomString1.name, xyz.cs1)
        members.put(StandardMembers.CustomString2.name, xyz.cs2)
        members.put(StandardMembers.CustomString3.name, xyz.cs3)
        return members
    }

    /**
     * Builds the [Tuple] for the given write action.
     * @param map the map in which the feature is being persisted.
     * @param collection the collection in which the feature is being persisted.
     * @param feature the feature.
     * @param action the [action][Action] being performed.
     * @param attachment the attachment.
     * @param atomic whether the write should be performed atomically.
     * @return the encoded [Tuple].
     */
    private fun buildTuple(
        map: NakshaMap,
        collection: NakshaCollection,
        feature: NakshaFeature,
        action: Action,
        attachment: ByteArray?,
        atomic: Boolean = false
    ): Tuple {
        val members = buildMembers(map, collection, feature, action, atomic)
        val dataEncoding = getDataEncoding(feature, collection)
        val xyz = feature.properties.xyz
        val actionBits = Int64(action.intValue.toLong())
        val featureNumber = feature.featureNumber
        val versionVal = version.txn and Int64(-4L) or actionBits
        val nextVersion: Int64 = if (map.id == Naksha.ADMIN_MAP && collection.id == Naksha.TRANSACTIONS_COL) versionVal else Int64(-1L)
        val dict = dictReader?.getEncodingDictionary(feature)
        val featureBytes = Naksha.encodeFeature(feature, dataEncoding, dict)
        val geoBytes = Naksha.encodeGeometry(feature.geometry)
        val refPoint = Naksha.encodeGeometry(feature.referencePoint)
        val tagsJson = Naksha.encodeTagList(xyz.tags)
        if (members is HeapBook) {
            members.put(StandardMembers.Geometry.name, geoBytes)
            members.put(StandardMembers.ReferencePoint.name, refPoint)
            members.put(StandardMembers.Tags.name, tagsJson)
            members.put(StandardMembers.Attachment.name, attachment)
        }
        return Tuple(
            storageNumber = storageNumber,
            mapNumber = map.number,
            collectionNumber = collection.number,
            featureNumber = featureNumber,
            version = Version(versionVal),
            nextVersion = nextVersion,
            members = members,
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
     * @param attachment the attachment.
     * @return the binary encoding of the [NakshaFeature] as [Tuple].
     */
    open fun created(
        map: NakshaMap,
        collection: NakshaCollection,
        feature: NakshaFeature,
        attachment: ByteArray?
    ): Tuple = buildTuple(map, collection, feature, Action.CREATED, attachment)

    /**
     * Convert the given [feature][NakshaFeature] into a [Tuple], when the feature was updated.
     * @param map the map in which the feature is going to be created.
     * @param collection the collection in which the feature is going to be created.
     * @param feature the feature that was created.
     * @param attachment the attachment.
     * @param atomic whether the write should be performed atomically.
     * @return the binary encoding of the [NakshaFeature] as [Tuple].
     */
    open fun updated(
        map: NakshaMap,
        collection: NakshaCollection,
        feature: NakshaFeature,
        attachment: ByteArray?,
        atomic: Boolean = false,
    ): Tuple = buildTuple(map, collection, feature, Action.UPDATED, attachment, atomic)

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
    ): Tuple = buildTuple(map, collection, feature, Action.DELETED, attachment)

    private fun getDataEncoding(feature: NakshaFeature, collection: NakshaCollection): DataEncoding =
        storage?.getDataEncoding(feature, collection) ?: Naksha.DEFAULT_DATA_ENCODING
}

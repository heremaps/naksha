@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.AtomicInt
import naksha.base.Int64
import naksha.jbon.IDictReader
import naksha.model.Metadata.Metadata_C.calculateHash
import naksha.model.Metadata.Metadata_C.calculateHereTile
import naksha.model.objects.*
import kotlin.js.JsExport

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
open class NakshaTx(
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
    val app_id: String,

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

    /**
     * The statistical transaction information, updated while this class is being used, should eventually be writted into the transaction-log of the storage.
     * @since 3.0
     */
    open val transaction = NakshaTransaction().setVersion(version)

    /**
     * The `updated_at` value being used for all [Tuple] created, basically just reads `transaction.time`.
     * @since 3.0
     */
    open val updatedAt
        get() = transaction.time

    /**
     * The transaction local identifier to create unique operations within this transaction.
     * @since 3.0
     */
    open val uid: AtomicInt = AtomicInt(0)

    /**
     * The default flags to use, if no better are available.
     *
     * @see [Naksha.DEFAULT_FLAGS]
     */
    open var defaultFlags = Naksha.DEFAULT_FLAGS

    /**
     * @see [defaultFlags]
     */
    open fun withDefaultFlags(flags: Int): NakshaTx {
        defaultFlags = flags
        return this
    }

    /**
     * Can be overridden by a storage implementation, to implement storage specific logic. If not overridden, just returns [defaultFlags].
     * @param feature the feature that should be encoded.
     * @return the [flags][Flags] to use.
     * @since 3.0
     */
    open fun getEncodingFlags(feature: NakshaFeature): Flags = defaultFlags

    /**
     * Method to create the new metadata, when performing the given operation, with the given feature as outcome of the operation, in the given session.
     *
     * - Throws [NakshaError.ILLEGAL_ARGUMENT], if the given arguments are not sufficient to generate the new metadata.
     * @param map the map into which to persist the feature.
     * @param collection the collection into which to persist the feature.
     * @param feature the new _(modified)_ state of the feature, for which the metadata should be created.
     * @param operation the [operation][Operation] that is performed.
     * @param action the [action][Action] being performed, if not given, it is expected that the given [operation][Operation] has a [fixed action][Operation.action].
     * @return the new metadata that is correct for the new state, based upon the given data.
     * @since 3.0.0
     * @see [NakshaTx]
     */
    protected open fun metadataOf(
        map: NakshaMap,
        collection: NakshaCollection,
        feature: NakshaFeature,
        operation: Operation,
        action: Action = operation.action ?: throw illegalArg("There is no default action defined for operation $operation"),
    ): Metadata {
        if (operation.action != action && operation.action != null) {
            throw illegalArg("The operation $operation is hard linked to the action ${operation.action}, therefore $action is an invalid action!")
        }
        var flags = getEncodingFlags(feature)
            .withOperation(operation)
            .withAction(action)
        val xyz = feature.properties.xyz
        val tn = TupleNumber(map.storageNumber, map.number, collection.number, feature.featureNumber, version, uid.getAndAdd(1))
        // TODO: Handle other operations like rebase!
        val base_tn: TupleNumber? = null
        val prev_tn: TupleNumber? = if (operation == Operation.CREATED) null else {
            xyz.guid?.tupleNumber ?: throw illegalArg("$operation requires that the feature has a UUID!")
        }
        val updatedAt: Int64 = this.updatedAt
        val createdAt: Int64? = if (prev_tn != null) {
            flags = flags.withCreateAt(true)
            xyz.createdAt
        } else null
        val author: String?
        val authorTs: Int64?
        if (xyz.author == null || xyz.author != this.author) {
            // Simulate SQL behavior:
            // If the previous author is null, then it differs from the current author, even when both are null!
            author = this.author
            authorTs = null // authorTs == updatedAt
            flags = flags.withAuthorTs(false)
        } else {
            // If the author stays the same as before, we need to remember the author and timestamp!
            author = xyz.author
            authorTs = xyz.authorTs
            flags = flags.withAuthorTs(true)
        }
        val cv0 = feature.properties.xyz.cv0
        if (cv0 != null) flags = flags.withCustomValue(0, true)
        val cv1 = feature.properties.xyz.cv1
        if (cv1 != null) flags = flags.withCustomValue(1, true)
        val cv2 = feature.properties.xyz.cv2
        if (cv2 != null) flags = flags.withCustomValue(2, true)
        val cv3 = feature.properties.xyz.cv3
        if (cv3 != null) flags = flags.withCustomValue(3, true)
        return Metadata(
            tupleNumber = tn,
            flags = flags,
            nextVersion = null,
            updatedAt = updatedAt,
            createdAt = createdAt,
            authorTs = authorTs,
            prevTupleNumber = prev_tn,
            baseTupleNumber = base_tn,
            changeCount = xyz.changeCount + 1,
            hash = calculateHash(feature),
            hereTile = calculateHereTile(feature),
            id = feature.id,
            appId = app_id,
            author = author,
            origin = null, // TODO: Fix this, we need to detect foreign features!
            target = null, // TODO: Fix this, we need to detect join operations!
            ft = if (collection.indexFeatureType) feature.featureType else null,
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
     * Convert the given [feature][NakshaFeature] into a [Tuple], when the feature was [created][Operation.CREATED].
     *
     * ### Note
     * This method can be used for `upsert` as well, just that on-conflict the following values have to be updated form the already existing feature:
     * - `prev_tn` - should be `tn` of the existing version
     * - `created_at` - should be `created_at` of the existing version.
     * - `cc` - _(change-count)_ should be set to the existing value + 1
     * - `author` - if the previous `author` is not the same as the current, then set to the current author _(author changed)_, otherwise set it to the previous one _(unchanged)_.
     * - `author_ts` - if the previous `author` is not the same as the current, set to `null` _(same as `updatedAt`)_, otherwise set to the previous value.
     * - `flags` - if the previous `author` is not the same as the current, set `authorTs` flag, otherwise clear it. Always set the `createdAt` flag _(basically these are only two binary AND/OR's)_, change the `operation` and `action` both to `UPDATED`.
     *
     * About author, beware that comparing `NULL` via `=` to any other value, is by definition always `false` in SQL, so if the previous author, the current author, or both are `null`, we treat this as a changed author!
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
        feature: NakshaFeature
    ): Tuple {
        val metadata = metadataOf(map, collection, feature, Operation.CREATED, Action.CREATED)
        val dictionary = dictReader?.getEncodingDictionary(feature)
        return Tuple(
            meta = metadata,
            feature = Naksha.encodeFeature(feature, metadata.flags, dictionary),
            tags = Naksha.encodeTags(feature.properties.xyz.tags.toTagMap(), metadata.flags, dictionary),
            referencePoint = Naksha.encodeGeometry(feature.referencePoint, metadata.flags),
            geo = Naksha.encodeGeometry(feature.geometry, metadata.flags),
            attachment = feature.attachment,
            complete = true
        )
    }

    /**
     * Convert the given [feature][NakshaFeature] into a [Tuple], when the feature was [updated][Operation.UPDATED].
     * @param map the map in which the feature is going to be created.
     * @param collection the collection in which the feature is going to be created.
     * @param feature the feature that was created.
     * @return the binary encoding of the [NakshaFeature] as [Tuple].
     */
    open fun updated(
        map: NakshaMap,
        collection: NakshaCollection,
        feature: NakshaFeature
    ): Tuple {
        val metadata = metadataOf(map, collection, feature, Operation.UPDATED, Action.UPDATED)
        val dictionary = dictReader?.getEncodingDictionary(feature)
        return Tuple(
            meta = metadata,
            feature = Naksha.encodeFeature(feature, metadata.flags, dictionary),
            tags = Naksha.encodeTags(feature.properties.xyz.tags.toTagMap(), metadata.flags, dictionary),
            referencePoint = Naksha.encodeGeometry(feature.referencePoint, metadata.flags),
            geo = Naksha.encodeGeometry(feature.geometry, metadata.flags),
            attachment = feature.attachment,
            complete = true
        )
    }

    /**
     * Convert the given [feature][NakshaFeature] into a [Tuple], when the feature was [deleted][Operation.DELETED].
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
        feature: NakshaFeature
    ): Tuple {
        val metadata = metadataOf(map, collection, feature, Operation.DELETED, Action.DELETED)
        val dictionary = dictReader?.getEncodingDictionary(feature)
        return Tuple(
            meta = metadata,
            feature = Naksha.encodeFeature(feature, metadata.flags, dictionary),
            tags = Naksha.encodeTags(feature.properties.xyz.tags.toTagMap(), metadata.flags, dictionary),
            referencePoint = Naksha.encodeGeometry(feature.referencePoint, metadata.flags),
            geo = Naksha.encodeGeometry(feature.geometry, metadata.flags),
            attachment = feature.attachment,
            complete = true
        )
    }
}
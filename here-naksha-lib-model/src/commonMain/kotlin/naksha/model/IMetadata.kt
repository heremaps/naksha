@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import kotlin.js.JsExport

/**
 * Interface to read the metadata of a [Tuple].
 * @since 3.0
 */
@JsExport
interface IMetadata {
    /**
     * The storage-number of the storage in which the tuple is stored.
     * @since 3.0
     */
    val storageNumber: Int64

    /**
     * The map-number of the map in which the tuple is stored.
     * @since 3.0
     */
    val mapNumber: Int

    /**
     * The collection-number of the collection in which the tuple is stored.
     * @since 3.0
     */
    val collectionNumber: Int

    /**
     * The feature-number of the tuple feature.
     * @since 3.0
     */
    val featureNumber: Int64

    /**
     * The partition-number in which this [Tuple], and all other [Tuple] of the feature, are stored. This is a value in the range `0..65535`.
     * @since 3.0
     */
    val partitionNumber: Int

    /**
     * The version of the [Tuple].
     *
     * This is the transaction-number of the transaction in which this [Tuple] was created, so the transaction to which this [Tuple] belongs.
     *
     * If the client wants to create metadata for internal purpose or to create a new feature, then it should use [Version.HEAD].
     * @since 3.0
     */
    val version: Version

    /**
     * The transaction local identifier of this [Tuple].
     * @since 3.0
     */
    val uid: Int

    /**
     * The transaction-number, being the same as [version].
     * @since 3.0
     */
    val txn: Int64

    /**
     * The [TupleNumber] of this [Tuple], which will be a combination of [storageNumber], [mapNumber], [collectionNumber], [partitionNumber], [txn], and [uid].
     * @since 3.0.0
     */
    val tupleNumber: TupleNumber

    /**
     * The [TupleNumber] of the next tuple, if a newer one is known.
     *
     * ### Warning
     * If this property is `null`, there is no guarantee that there is no newer state, so this acts as an inverse bloom filter, if the value is set, you can be sure that this [Tuple] is a historic one, otherwise it is only likely that this [Tuple] is the latest one _(HEAD state)_.
     *
     * Not all storages will support this property, therefore use should be done with care. If the storage does not support it, finding the next state can be done by querying for the [Tuple] that has [prevTupleNumber] set to this [tupleNumber].
     * @since 3.0
     */
    val nextTupleNumber: TupleNumber?

    /**
     * The [TupleNumber] of the previous tuple, if any.
     * @since 3.0
     */
    val prevTupleNumber: TupleNumber?

    /**
     * The [TupleNumber] of the _base_ [Tuple], when this [Tuple] is the result of an auto-merge.
     *
     * The _BASE_ [Tuple] is the shared previous state that the _principal_, and other concurrently operating _principals_, changed (base of the changes). The previous tuple, referred to via [prevTupleNumber], is the _OTHER_ state, so the state that the other _principals_ produced. The state this _principal_ produced is not persisted, we call it _NEW_. This [Tuple] (_HEAD_) is the result of creating a difference between _OTHER_ and _BASE_ (what others changes), and then adding the difference between _NEW_ and _BASE_ (what this _principal_ changed) into a _patch_, applying the _patch_ to _BASE_, resulting in _HEAD_.
     *
     * Keeping track of _BASE_ allows to calculate back _NEW_, which was never persisted, and is the state the _principal_ originally created, before the auto-merge was done. Calculating it back is done by reverting the above described. So, calculate the difference between _OTHER_ (previous tuple) and _BASE_, resulting in other-diff, then calculate the difference between _HEAD_ and _BASE_, resulting in total-diff, finally subtracting the other-diff from the total-diff, resulting in a patch that only contains the changes done by this _principal_, and when applied to _BASE_, produces _NEW_.
     * @since 3.0
     */
    val baseTupleNumber: TupleNumber?

    /**
     * The flags.
     * @since 3.0
     */
    val flags: Flags

    /**
     * The epoch milliseconds, when the feature was created, _null_, when this is the same as [updatedAt], which means, this is the first version of the feature.
     *
     * The recommended usage is `meta.createdAt ?: meta.updatedAt`.
     * @since 3.0
     */
    val createdAt: Int64?

    /**
     * The epoch milliseconds when the author changed, _null_, when this is the same as [updatedAt].
     *
     * The recommended usage is `meta.createdAt ?: meta.updatedAt`.
     * @since 3.0
     */
    val authorTs: Int64?

    /**
     * The epoch milliseconds when this tuple was created.
     *
     * **This is the time when this [Tuple] was created, not when the first [Tuple] of the feature was created!**
     * @since 3.0
     */
    val updatedAt: Int64

    /**
     * The amount of changes done to the feature, the counter starts with 1.
     * @since 3.0
     */
    val changeCount: Int

    /**
     * The Naksha storage hash about the feature.
     * @since 3.0
     */
    val hash: Int

    /**
     * The binary [HERE tile number][naksha.geo.HereTile] calculated from the [Tuple.referencePoint].
     * @since 3.0
     */
    val hereTile: Int

    /**
     * The feature-id.
     * @since 3.0
     */
    val id: String

    /**
     * The application identifier of the application that performed the last change.
     * @since 3.0
     */
    val appId: String

    /**
     * The last author that made a change at [authorTs].
     * @since 3.0
     */
    val author: String?

    /**
     * The stringified [Guid] from where the feature originates.
     * @since 3.0
     * @see [Operation.FORKED]
     * @see [Operation.SPLIT]
     * @see [Operation.JOINED]
     * @see [Operation.REBASED]
     */
    val origin: String?

    /**
     * The [origin] as [Guid]; _null_ if the [origin] is _null_, empty string, or no valid [Guid].
     * @since 3.0
     * @see [Operation.FORKED]
     * @see [Operation.SPLIT]
     * @see [Operation.JOINED]
     * @see [Operation.REBASED]
     */
    val originGuid: Guid?

    /**
     * The stringified [Guid] of the [Tuple] into which a feature was merged.
     * @since 3.0
     * @see [Operation.JOINED]
     */
    val target: String?

    /**
     * The [target] as [Guid]; _null_ if the [target] is _null_, empty string, or no valid [Guid].
     * @since 3.0
     * @see [Operation.JOINED]
     */
    val targetGuid: Guid?

    /**
     * A custom feature-type that is indexed, if not being _null_ _(partial index)_.
     * @since 3.0
     */
    val ft: String?

    /**
     * A custom value that is indexed, if not being _null_ _(partial index)_.
     * @since 3.0
     */
    val cv0: Double?

    /**
     * A custom value that is indexed, if not being _null_ _(partial index)_.
     * @since 3.0
     */
    val cv1: Double?

    /**
     * A custom value that is indexed, if not being _null_ _(partial index)_.
     * @since 3.0
     */
    val cv2: Double?

    /**
     * A custom value that is indexed, if not being _null_ _(partial index)_.
     * @since 3.0
     */
    val cv3: Double?

    /**
     * A custom string that is indexed, if not being _null_ _(partial index)_.
     * @since 3.0
     */
    val cs0: String?

    /**
     * A custom string that is indexed, if not being _null_ _(partial index)_.
     * @since 3.0
     */
    val cs1: String?

    /**
     * A custom string that is indexed, if not being _null_ _(partial index)_.
     * @since 3.0
     */
    val cs2: String?

    /**
     * A custom string that is indexed, if not being _null_ _(partial index)_.
     * @since 3.0
     */
    val cs3: String?

    /***
     * The [Guid] of the [Tuple], basically just a combination of [tupleNumber] and [id].
     * @since 3.0
     */
    val guid: Guid

    /**
     * Returns the [updatedAt] timestamp.
     * @return the [updatedAt] timestamp.
     * @since 3.0
     */
    fun useUpdatedAt(): Int64 = updatedAt

    /**
     * Returns the [createdAt] timestamp.
     * @return the [createdAt] timestamp.
     * @since 3.0
     */
    fun useCreatedAt(): Int64 = createdAt ?: updatedAt

    /**
     * Returns the [authorTs] timestamp.
     * @return the [authorTs] timestamp.
     * @since 3.0
     */
    fun useAuthorTs(): Int64 = authorTs ?: updatedAt
}
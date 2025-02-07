@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.AtomicInt
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaMap
import naksha.model.objects.NakshaTransaction
import kotlin.js.JsExport

/**
 * A write-request.
 */
@JsExport
interface IWriteSession: IReadSession {

    /**
     * The `uid` counter (unique identifier within a transaction).
     *
     * This value is reset to `0` after every [commit] or [rollback].
     * @since 3.0.0
     */
    val uid: AtomicInt

    /**
     * Acquire a storage lock, that is automatically released when the session is [closed][close].
     *
     * @param lockId the unique identifier for the lock.
     * @since 3.0.0
     */
    @v30_experimental
    fun acquireSessionLock(lockId: String): ILock

    /**
     * Acquire a storage lock, that is automatically released when the transaction is [committed][commit], [rolled back][rollback], or when the session is [closed][close].
     *
     * @param lockId the unique identifier for the lock.
     * @since 3.0.0
     */
    @v30_experimental
    fun acquireTransactionLock(lockId: String): ILock
    /**
     * Commit all pending changes in the current transaction. Returns the underlying connection back into the connection pool.
     * @since 2.0.7
     */
    fun commit()

    /**
     * Rollback (revert) all pending changes in the current transaction. Returns the underlying connection back into the connection pool.
     * @since 2.0.7
     */
    fun rollback()

    /**
     * Returns the current transaction, if none is yet started, start a new one. Starting a transaction, requires to allocate a sticky connection, and therefore disables parallel reading.
     * @return the transaction.
     * @since 3.0.0
     */
    fun useTransaction(): NakshaTransaction

    /**
     * Returns the current transaction, if any is available.
     * @return the current transaction, if any is available; _null_ otherwise.
     * @since 3.0.0
     */
    fun getTransaction(): NakshaTransaction?

    /**
     * Create a new tuple-number for the given feature.
     * @param map the map in which the feature is going to be stored.
     * @param collection the collection in which the feature is going to be stored.
     * @param featureId the `id` of the feature to store.
     * @return a new [TupleNumber] for the new feature state.
     */
    fun newTupleNumber(
        map: NakshaMap,
        collection: NakshaCollection,
        featureId: String
    ): TupleNumber {
        val mapNumber = map.number ?: throw NakshaException(ILLEGAL_ARGUMENT, "Missing map.number")
        val collectionNumber = collection.number ?: throw NakshaException(ILLEGAL_ARGUMENT, "Missing collection.number")
        val pn = Naksha.partitionNumber(featureId)
        val version = useTransaction().version
        val uid = uid.getAndAdd(1)
        return TupleNumber(storage.number, mapNumber, collectionNumber, pn, version, uid)
    }

    /**
     * Helper method to create the new metadata, when performing the given operation, with the given feature as outcome of the operation, in the given session.
     *
     * Actually this method should be used, when the new metadata need to be calculated.
     * - Throws [NakshaError.ILLEGAL_ARGUMENT], if the given arguments are not sufficient to generate the new metadata.
     * @param feature the new _(modified)_ state of the feature, for which the metadata should be created.
     * @param tupleNumber the new [TupleNumber] that is generated for the new state, see [newTupleNumber].
     * @param operation the [operation][Operation] that is performed.
     * @param action the [action][Action] being performed, if not given, it is expected that the given [operation][Operation] has a [fixed action][Operation.action].
     * @return the new metadata that is correct for the new state, based upon the given data.
     * @since 3.0.0
     * @see [newTupleNumber]
     */
    fun metadataFor(
        feature: NakshaFeature,
        tupleNumber: TupleNumber,
        operation: Operation,
        action: Action = operation.action ?:
          throw NakshaException(ILLEGAL_ARGUMENT, "There is no default action defined for operation $operation"),
    ): Metadata = Metadata.forOperation(this, feature, tupleNumber, operation, action)

    /**
     * Helper to create a [Tuple] from a [NakshaFeature] for a [created-operation][Operation.CREATED].
     * @param map the map in which the feature is going to be stored.
     * @param collection the collection in which the feature is going to be stored.
     * @param feature the feature to store.
     * @param tupleNumber if not provided, automatically generated from this session.
     * @return the binary encoding of the [NakshaFeature] as [Tuple].
     */
    fun created(
        map: NakshaMap,
        collection: NakshaCollection,
        feature: NakshaFeature,
        tupleNumber: TupleNumber? = null
    ): Tuple {
        val realTupleNumber = tupleNumber ?: newTupleNumber(map, collection, feature.id)
        val metadata = metadataFor(feature, realTupleNumber, Operation.CREATED)
        val dictionary = storage.getEncodingDictionary(feature, collection)
        return Tuple(
            meta = metadata,
            feature = Naksha.encodeFeature(feature, metadata.flags, dictionary),
            geo = Naksha.encodeGeometry(feature.geometry, metadata.flags),
            referencePoint = Naksha.encodeGeometry(feature.referencePoint, metadata.flags),
            tags = Naksha.encodeTags(feature.properties.xyz.tags.toTagMap(), metadata.flags, dictionary),
            attachment = feature.attachment,
            complete = true
        )
    }

    /**
     * Helper to create a [Tuple] from a [NakshaFeature] for a [updated-operation][Operation.UPDATED].
     * @param map the map in which the feature is going to be stored.
     * @param collection the collection in which the feature is going to be stored.
     * @param feature the feature to store.
     * @param tupleNumber if not provided, automatically generated from this session.
     * @return the binary encoding of the [NakshaFeature] as [Tuple].
     */
    fun updated(
        map: NakshaMap,
        collection: NakshaCollection,
        feature: NakshaFeature,
        tupleNumber: TupleNumber? = null
    ): Tuple {
        val realTupleNumber = tupleNumber ?: newTupleNumber(map, collection, feature.id)
        val metadata = metadataFor(feature, realTupleNumber, Operation.UPDATED)
        val dictionary = storage.getEncodingDictionary(feature, collection)
        return Tuple(
            meta = metadata,
            feature = Naksha.encodeFeature(feature, metadata.flags, dictionary),
            geo = Naksha.encodeGeometry(feature.geometry, metadata.flags),
            referencePoint = Naksha.encodeGeometry(feature.referencePoint, metadata.flags),
            tags = Naksha.encodeTags(feature.properties.xyz.tags.toTagMap(), metadata.flags, dictionary),
            attachment = feature.attachment,
            complete = true
        )
    }

    /**
     * Helper to create a [Tuple] from a [NakshaFeature] for a [deleted-operation][Operation.DELETED].
     * @param map the map in which the feature is going to be stored.
     * @param collection the collection in which the feature is going to be stored.
     * @param feature the feature to store.
     * @param tupleNumber if not provided, automatically generated from this session.
     * @return the binary encoding of the [NakshaFeature] as [Tuple].
     */
    fun deleted(
        map: NakshaMap,
        collection: NakshaCollection,
        feature: NakshaFeature,
        tupleNumber: TupleNumber? = null
    ): Tuple {
        val realTupleNumber = tupleNumber ?: newTupleNumber(map, collection, feature.id)
        val metadata = metadataFor(feature, realTupleNumber, Operation.DELETED)
        val dictionary = storage.getEncodingDictionary(feature, collection)
        return Tuple(
            meta = metadata,
            feature = Naksha.encodeFeature(feature, metadata.flags, dictionary),
            geo = Naksha.encodeGeometry(feature.geometry, metadata.flags),
            referencePoint = Naksha.encodeGeometry(feature.referencePoint, metadata.flags),
            tags = Naksha.encodeTags(feature.properties.xyz.tags.toTagMap(), metadata.flags, dictionary),
            attachment = feature.attachment,
            complete = true
        )
    }

    // TODO: Implement all operations!

//    fun rebased(): Tuple {
//        TODO("Implement me")
//    }
//
//    fun forked(): Tuple {
//        TODO("Implement me")
//    }
//
//    fun merged(): Tuple {
//        TODO("Implement me")
//    }
//
//    fun split(): Tuple {
//        TODO("Implement me")
//    }
//
//    fun joined(): Tuple {
//        TODO("Implement me")
//    }
}
@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.toInt64
import naksha.model.Naksha.NakshaCompanion.featureNumber
import naksha.model.Naksha.NakshaCompanion.hashId
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaMap
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * Helper methods for [ISession] or [IWriteSession].
 * @since 3.0
 */
@JsExport
class SessionUtil {
    companion object SessionUtil_C {
        /**
         * Create a new tuple-number for the given feature.
         * @param session the session to use.
         * @param map the map in which the feature is going to be stored.
         * @param collection the collection in which the feature is going to be stored.
         * @param featureId the `id` of the feature to store.
         * @return a new [TupleNumber] for the new feature state.
         */
        @JsName("newTupleNumberById")
        @JsStatic
        @JvmStatic
        fun newTupleNumber(
            session: IWriteSession,
            map: NakshaMap,
            collection: NakshaCollection,
            featureId: String
        ): TupleNumber {
            val storageNumber = session.storage.number
            val mapNumber = map.number ?: throw NakshaException(ILLEGAL_ARGUMENT, "Missing map.number")
            val collectionNumber = collection.number ?: throw NakshaException(ILLEGAL_ARGUMENT, "Missing collection.number")
            val md5 = hashId(featureId)
            val featureNumber = featureNumber(md5)
            val version = session.useTransaction().version
            val uid = session.uid.getAndAdd(1)
            return TupleNumber(storageNumber, mapNumber, collectionNumber, featureNumber, version, uid)
        }

        /**
         * Create a new tuple-number for the given feature.
         * @param session the session to use.
         * @param map the map in which the feature is going to be stored.
         * @param collection the collection in which the feature is going to be stored.
         * @param feature the feature to store.
         * @return a new [TupleNumber] for the new feature state.
         */
        @JsStatic
        @JvmStatic
        fun newTupleNumber(
            session: IWriteSession,
            map: NakshaMap,
            collection: NakshaCollection,
            feature: NakshaFeature
        ): TupleNumber {
            val storageNumber = session.storage.number
            val mapNumber = map.number ?: throw NakshaException(ILLEGAL_ARGUMENT, "Missing map.number")
            val collectionNumber = collection.number ?: throw NakshaException(ILLEGAL_ARGUMENT, "Missing collection.number")
            val md5 = hashId(feature.id)
            val featureNumber = featureNumber(md5)
            val version = session.useTransaction().version
            val uid = session.uid.getAndAdd(1)
            return TupleNumber(storageNumber, mapNumber, collectionNumber, featureNumber, version, uid)
        }

        /**
         * Create a new tuple-number for the collection.
         * @param session the session to use.
         * @param map the map in which the collection is located.
         * @param collection the collection that is changed.
         * @return a new [TupleNumber] for the new collection state.
         */
        @JsName("newTupleNumberForCollection")
        @JsStatic
        @JvmStatic
        fun newTupleNumber(
            session: IWriteSession,
            map: NakshaMap,
            collection: NakshaCollection
        ): TupleNumber {
            val storageNumber = session.storage.number
            val mapNumber = map.number ?: throw NakshaException(ILLEGAL_ARGUMENT, "Missing map.number")
            val collectionNumber = collection.number ?: throw NakshaException(ILLEGAL_ARGUMENT, "Missing collection.number")
            val featureNumber = collectionNumber.toInt64()
            val version = session.useTransaction().version
            val uid = session.uid.getAndAdd(1)
            return TupleNumber(storageNumber, mapNumber, collectionNumber, featureNumber, version, uid)
        }

        /**
         * Helper method to create the new metadata, when performing the given operation, with the given feature as outcome of the operation, in the given session.
         *
         * Actually this method should be used, when the new metadata need to be calculated.
         * - Throws [NakshaError.ILLEGAL_ARGUMENT], if the given arguments are not sufficient to generate the new metadata.
         * @param session the session to use.
         * @param feature the new _(modified)_ state of the feature, for which the metadata should be created.
         * @param tupleNumber the new [TupleNumber] that is generated for the new state, see [newTupleNumber].
         * @param operation the [operation][Operation] that is performed.
         * @param action the [action][Action] being performed, if not given, it is expected that the given [operation][Operation] has a [fixed action][Operation.action].
         * @return the new metadata that is correct for the new state, based upon the given data.
         * @since 3.0.0
         * @see [newTupleNumber]
         */
        fun metadataFor(
            session: IWriteSession,
            feature: NakshaFeature,
            tupleNumber: TupleNumber,
            operation: Operation,
            action: Action = operation.action ?:
            throw NakshaException(ILLEGAL_ARGUMENT, "There is no default action defined for operation $operation"),
        ): Metadata = Metadata.forOperation(session, feature, tupleNumber, operation, action)

        /**
         * Helper to create a [Tuple] from a [NakshaFeature] for a [created-operation][Operation.CREATED].
         * @param session the session to use.
         * @param map the map in which the feature is going to be stored.
         * @param collection the collection in which the feature is going to be stored.
         * @param feature the feature to store.
         * @param tupleNumber if not provided, automatically generated from this session.
         * @return the binary encoding of the [NakshaFeature] as [Tuple].
         */
        fun created(
            session: IWriteSession,
            map: NakshaMap,
            collection: NakshaCollection,
            feature: NakshaFeature,
            tupleNumber: TupleNumber? = null
        ): Tuple {
            val realTupleNumber = tupleNumber ?: newTupleNumber(session, map, collection, feature.id)
            val metadata = metadataFor(session, feature, realTupleNumber, Operation.CREATED)
            val dictionary = session.storage.getEncodingDictionary(feature, collection)
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
         * @param session the session to use.
         * @param map the map in which the feature is going to be stored.
         * @param collection the collection in which the feature is going to be stored.
         * @param feature the feature to store.
         * @param tupleNumber if not provided, automatically generated from this session.
         * @return the binary encoding of the [NakshaFeature] as [Tuple].
         */
        fun updated(
            session: IWriteSession,
            map: NakshaMap,
            collection: NakshaCollection,
            feature: NakshaFeature,
            tupleNumber: TupleNumber? = null
        ): Tuple {
            val realTupleNumber = tupleNumber ?: newTupleNumber(session, map, collection, feature.id)
            val metadata = metadataFor(session, feature, realTupleNumber, Operation.UPDATED)
            val dictionary = session.storage.getEncodingDictionary(feature, collection)
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
         * @param session the session to use.
         * @param map the map in which the feature is going to be stored.
         * @param collection the collection in which the feature is going to be stored.
         * @param feature the feature to store.
         * @param tupleNumber if not provided, automatically generated from this session.
         * @return the binary encoding of the [NakshaFeature] as [Tuple].
         */
        fun deleted(
            session: IWriteSession,
            map: NakshaMap,
            collection: NakshaCollection,
            feature: NakshaFeature,
            tupleNumber: TupleNumber? = null
        ): Tuple {
            val realTupleNumber = tupleNumber ?: newTupleNumber(session, map, collection, feature.id)
            val metadata = metadataFor(session, feature, realTupleNumber, Operation.DELETED)
            val dictionary = session.storage.getEncodingDictionary(feature, collection)
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
}
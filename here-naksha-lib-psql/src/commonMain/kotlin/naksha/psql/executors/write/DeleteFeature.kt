package naksha.psql.executors.write

import naksha.model.*
import naksha.model.Metadata.Metadata_C.calculateHereTile
import naksha.model.Metadata.Metadata_C.calculateHash
import naksha.model.NakshaError.NakshaErrorCompanion.MAP_NOT_FOUND
import naksha.model.objects.NakshaFeature
import naksha.model.request.ReadFeatures
import naksha.model.request.SuccessResponse
import naksha.psql.PgCollection
import naksha.psql.PgSession
import naksha.psql.executors.PgReader
import naksha.psql.executors.PgWriter
import naksha.psql.executors.WriteExt
import naksha.psql.executors.write.WriteFeatureUtils.resolveFlags

class DeleteFeature(
    private val session: PgSession,
    private val writeExecutor: WriteExecutor
) {
    fun execute(collection: PgCollection, write: WriteExt, tupleList: TupleList): TupleNumber {
        val collectionId = write.id ?: throw NakshaException(NakshaError.ILLEGAL_ARGUMENT, "No feature ID provided")
        val mapId = collection.map.id
        val map = session.storage.adminMap.getPgMapById(session.useConnection(), mapId) ?: throw NakshaException(MAP_NOT_FOUND, "Map $mapId not found")
        val tupleNumber = TupleNumber(map.storage.number, map.number, collection.number, write.feature!!.featureNumber, session.useTx().version, session.useTx().uid.addAndGet(1))
        val flags = resolveFlags(collection, session).withAction(Action.DELETED)

        val readFeatures = ReadFeatures()
        readFeatures.collectionIds.add(collection.id)
        readFeatures.featureIds.add(collectionId)
        val response = PgReader(session, readFeatures).execute().proxy(SuccessResponse::class)

        // Only modify head, hst and del tables if feature exists
        if (response.features!!.isNotEmpty()) {
            // If hst table enabled
            collection.history?.let { hstTable ->
                // copy head state into hst with txn_next === txn
                writeExecutor.copyHeadToHst(collection = collection, featureId = collectionId)
                // also copy head state into hst with txn_next === txn and action DELETED as a tombstone state
                writeExecutor.copyHeadToHst(
                    collection = collection,
                    tupleNumber = tupleNumber,
                    flags = flags,
                    featureId = collectionId,
                )
            }

            // If del table enabled, copy head state into del, with action DELETED and txn_next === txn as a tombstone state
            collection.deleted?.let { delTable ->
                writeExecutor.copyHeadToDel(
                    collection = collection,
                    tupleNumber = tupleNumber,
                    flags = flags,
                    featureId = collectionId,
                )
            }

            writeExecutor.removeFeatureFromHead(collection, collectionId)
            val feature = response.features!!.first()!! //already checked that feature list is not empty
            // val metadata = response.tupleList!!.first()?.tuple?.meta!!
            val tuple = session.useTx().created(map.nakshaMap, collection.nakshaCollection, feature)
            return PgWriter.cachedTupleNumber(write, tuple, tupleList)
        }
        return tupleNumber
    }

    private fun metaForDeleted(
        previousMetadata: Metadata,
        feature: NakshaFeature,
        flags: Flags,
        collection: PgCollection
    ): Metadata {
        val versionTime = session.useTransaction().time
        val tupleNumber = TupleNumber(
            session.storage.number,
            collection.map.number,
            collection.number,
            previousMetadata.featureNumber,
            session.useTransaction().version,
            session.uid.getAndAdd(1)
        )
        return previousMetadata.copy(
            tupleNumber = tupleNumber,
            updatedAt = versionTime,
            authorTs = if (session.options.author == null) previousMetadata.authorTs else versionTime,
            prevTupleNumber = previousMetadata.tupleNumber,
            hash = calculateHash(feature, session.options.excludePaths, session.options.excludeFn),
            changeCount = previousMetadata.changeCount + 1,
            hereTile = calculateHereTile(feature),
            flags = flags,
            appId = session.options.appId,
            author = session.options.author ?: previousMetadata.author,
            id = feature.id
        )
    }
}
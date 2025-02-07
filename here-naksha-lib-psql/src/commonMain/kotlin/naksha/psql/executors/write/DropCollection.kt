package naksha.psql.executors.write

import naksha.base.Platform.PlatformCompanion.logger
import naksha.model.*
import naksha.model.Naksha.NakshaCompanion.COLLECTIONS_COL
import naksha.psql.*
import naksha.psql.executors.WriteExt

class DropCollection(private val session: PgSession) {

    fun execute(map: PgMap, write: WriteExt): TupleNumber? {
        if(write.collectionId != COLLECTIONS_COL){
            throw NakshaException(
                NakshaError.ILLEGAL_ARGUMENT,
                "Expected $COLLECTIONS_COL collectionId when dropping collections"
            )
        }
        val collectionId = write.featureId ?: throw NakshaException(
            NakshaError.ILLEGAL_ARGUMENT,
            "DROP without collectionId (expected in write's 'featureId')"
        )
        // If no such collection exists, we're done.
        val pgCollection = map.storage.adminMap.getPgCollectionById(session.useConnection(), map, collectionId) ?: return null
        val conn = session.useConnection()
        try {
            /**
             * TODO:
             *      The code below does not cover writing deleted collection to history
             *      This will be addressed in: CASL-537
             */
            map.storage.adminMap.deletePgCollection(session.useConnection(), pgCollection)
            // This job is now done by admin-map!
            //removeCollectionFromVirtualCollections(collectionId, conn)
            conn.commit()
            return collectionTupleNumber(pgCollection)
        } catch (e: Exception) {
            logger.info("Exception when dropping collection $collectionId, rolling back and throwing exception down the chain", e)
            conn.rollback()
            throw e
        }
    }

    private fun collectionTupleNumber(collection: PgCollection): TupleNumber =
        TupleNumber(collection.map.storage.number, collection.map.number, collection.number, 0, session.useTransaction().version, newUid())

    private fun newUid(): Int = session.uid.getAndAdd(1)
}
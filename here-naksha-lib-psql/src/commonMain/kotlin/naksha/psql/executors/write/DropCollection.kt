package naksha.psql.executors.write

import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.PlatformLogger
import naksha.model.*
import naksha.model.Naksha.NakshaCompanion.VIRT_COLLECTIONS
import naksha.model.Naksha.NakshaCompanion.VIRT_COLLECTIONS_QUOTED
import naksha.model.objects.NakshaCollection
import naksha.psql.*
import naksha.psql.executors.WriteExt

class DropCollection(private val session: PgSession) {

    fun execute(map: PgMap, write: WriteExt): TupleNumber {
        if(write.collectionId != VIRT_COLLECTIONS){
            throw NakshaException(
                NakshaError.ILLEGAL_ARGUMENT,
                "Expected $VIRT_COLLECTIONS collectionId when dropping collections"
            )
        }
        val collectionId = write.featureId ?: throw NakshaException(
            NakshaError.ILLEGAL_ARGUMENT,
            "DROP without collectionId (expected in write's 'featureId')"
        )
        val pgCollection = map[collectionId]
        val tupleNumber = collectionTupleNumber(pgCollection)
        val conn = session.usePgConnection()
        try {
            /**
             * TODO:
             *      The code below does not cover writing deleted collection to history
             *      This will be addressed in: CASL-537
             */
            pgCollection.drop(conn)
            removeCollectionFromVirtualCollections(collectionId, conn)
            conn.commit()
            return tupleNumber
        } catch (e: Exception) {
            logger.info("Exception when dropping collection $collectionId, rolling back and throwing exception down the chain", e)
            conn.rollback()
            throw e
        }
    }

    private fun removeCollectionFromVirtualCollections(collectionId: String, connection: PgConnection){
        connection.execute(
            sql = "DELETE FROM $VIRT_COLLECTIONS_QUOTED WHERE ${PgColumn.id.ident}=$1",
            args = arrayOf(collectionId)
        ).close()
    }

    private fun collectionTupleNumber(collection: PgCollection): TupleNumber =
        TupleNumber(StoreNumber(collection.map.number, collection.number, 0), session.version(), newUid())

    private fun newUid(): Int = session.uid.getAndAdd(1)
}
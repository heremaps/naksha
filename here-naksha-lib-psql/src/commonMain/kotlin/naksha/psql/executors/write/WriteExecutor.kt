package naksha.psql.executors.write

import naksha.model.*
import naksha.model.objects.NakshaFeature
import naksha.psql.PgCollection
import naksha.psql.PgTable

interface WriteExecutor {

    fun removeFeatureFromDel(collection: PgCollection, featureId: String)

    fun executeInsert(
        collection: PgCollection,
        tuple: Tuple,
        feature: NakshaFeature
    )

    fun finish()

    fun copyHeadToHst(collection: PgCollection, tupleNumber: TupleNumber? = null, flags: Flags? = null, featureId: String)

    fun copyHeadToDel(collection: PgCollection, tupleNumber: TupleNumber? = null, flags: Flags? = null, featureId: String)

    fun updateFeatureInHead(
        collection: PgCollection,
        tuple: Tuple,
        feature: NakshaFeature,
        newVersion: Version,
        previousMetadata: Metadata
    )
}
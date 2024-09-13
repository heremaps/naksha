package naksha.psql.executors.write

import naksha.model.Tuple
import naksha.model.objects.NakshaFeature
import naksha.psql.PgCollection

interface WriteExecutor {

    fun removeFeatureFromDel(collection: PgCollection, featureId: String)

    fun executeInsert(
        collection: PgCollection,
        tuple: Tuple,
        feature: NakshaFeature
    )

    fun finish()
}
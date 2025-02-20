@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package naksha.model

import naksha.model.request.FeatureTuple

/**
 * Platform specific code to load tuples into [TupleCache].
 * @since 3.0
 */
internal expect class TupleLoader {
    companion object TupleLoader_C {
        /**
         * Query all storages in parallel using new connections, if possible.
         * @param features the [FeatureTuple] for which to load the [Tuple]
         * @since 3.0
         */
        internal fun <LIST : List<FeatureTuple?>> loadParallel(features: LIST)
    }
}
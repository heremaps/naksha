@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package naksha.model

import naksha.model.request.FeatureTuple

internal actual class TupleLoader {
    actual companion object TupleLoader_C {
        internal actual fun <LIST : List<FeatureTuple?>> loadParallel(features: LIST) {
            // TODO: Implement me!
        }
    }
}
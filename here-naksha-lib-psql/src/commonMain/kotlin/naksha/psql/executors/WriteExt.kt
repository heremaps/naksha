package naksha.psql.executors

import naksha.base.NotNullProperty
import naksha.model.request.Write

internal class WriteExt : Write() {
    companion object ExtendedWrite_C {
        private val INT_0 = NotNullProperty<WriteExt, Int>(Int::class) { _, _ -> 0 }
    }

    /**
     * The position in the original list to reorder the results.
     */
    var i by INT_0
}
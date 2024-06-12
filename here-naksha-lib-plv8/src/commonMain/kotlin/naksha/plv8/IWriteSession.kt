package naksha.plv8

import naksha.model.NakshaContext
import naksha.model.NakshaFeatureProxy
import naksha.model.response.Response
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class IWriteSession(
    context: NakshaContext,
    stmtTimeout: Int,
    lockTimeout: Int
): IReadSession(context, stmtTimeout, lockTimeout) {

    abstract fun writeFeature(feature: NakshaFeatureProxy): Response

    abstract fun commit()

    abstract fun rollback()

    abstract fun close()
}